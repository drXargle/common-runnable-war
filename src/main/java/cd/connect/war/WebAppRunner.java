package cd.connect.war;

import cd.connect.war.handler.MetaDataHandler;
import cd.connect.war.watcher.ShutdownWatcher;
import cd.connect.war.watcher.StdinWatcher;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.AbstractSessionManager;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * This is the guts of the Jetty runner. It mixes in detection of dev mode and production mode, but essentially
 * sets the server up in exactly the same way, which is important for consistency.
 */
public class WebAppRunner {

	public static final String WEBAPP_HTTP_PORT_PROPERTY = "webapp.http.port";
	public static final String WEBAPP_CONTEXT_PROPERTY = "webapp.context";
	public static final String WEBAPP_SHUTDOWN_STDIN_PROPERTY = "webapp.shutdown.stdin";
	public static final String WEBAPP_SHUTDOWN_TIMEOUT_PROPERTY = "webapp.shutdown.timeout";
	public static final String WEBAPP_LOCKFILE_PROPERTY = "webapp.lockfile";
	public static final String WEBAPP_WEBDEFAULT_XML_LOCATION = "webapp.webdefaultxml";
	public static final String WEBAPP_SECURE_COOKIES_PROPERTY = "webapp.cookies.secure";
	public static final String WEBAPP_EXTRA_CONFIGURATION_CLASSES = "webapp.configClasses";
	public static final String WEBAPP_WAR_FILENAME = "webapp.warFile";
	public static final String WEBAPP_REQUEST_HEADER_SIZE = "webapp.header.request.size";
	public static final String WEBAPP_RESPONSE_HEADER_SIZE = "webapp.header.response.size";
	public static final String WEBAPP_HEADER_CACHE_SIZE = "webapp.header.cache.size";
	public static final String WEBAPP_OUTPUT_BUFFER_SIZE = "webapp.output.buffer.size";

	public static final String WEBDEFAULT_XML = "cd/connect/war/webdefault.xml";
	public static final String WEBDEFAULT_DEV_XML = "cd/connect/war/webdefault-dev.xml";

	protected static final int WEBAPP_HTTP_PORT_DEFAULT = 8090;
	protected static final int WEBAPP_SHUTDOWN_TIMEOUT_DEFAULT = 12000;
	protected static final String WEBAPP_CONTEXT_DEFAULT = "/";
	protected static final int HEADER_SIZE_BASE = 1024;
	protected static final int WEBAPP_REQUEST_HEADER_SIZE_DEFAULT = 8;
	protected static final int WEBAPP_RESPONSE_HEADER_SIZE_DEFAULT = 8;
	protected static final int WEBAPP_OUTPUT_BUFFER_SIZE_DEFAULT = 32;
	protected static final int WEBAPP_HEADER_CACHE_SIZE_DEFAULT = 1;

	// basic required configuration classes in this particular order
	private static String[] JETTY_CONFIGURATION_CLASSES =
					{
									ScanConfiguration.class.getSimpleName(),
									ScannedWebXmlConfiguration.class.getSimpleName(),
									"org.eclipse.jetty.webapp.JettyWebXmlConfiguration"
					};

	private static final Logger logger = LoggerFactory.getLogger(WebAppRunner.class);

	private File war;
	private Server server;
	private HandlerList serverHandler;
	private StatisticsHandler statistics;
	private WebAppContext context;
	private WebAppLockFile lockfile;
	private int port;

	/* historical reasons */
	public static void run(File war) {
		run(war, null);
	}

	/**
	 * Runs the WAR file. WebAppBooter calls this.
	 *
	 * @param war - the WAR file or NULL if we are in dev mode.
	 */
	public static void run(File war, String[] args) {
		new WebAppRunner(war).run();
	}

	/**
	 * @param war the WAR file or NULL if we are in dev mode. Can be a directory. With Servlet3 spec, this doesn't even need to contain the web.xml!
	 */
	public WebAppRunner(File war) {
		if (war != null) {
			System.setProperty(WEBAPP_WAR_FILENAME, war.toURI().toString());
		}

		this.war = war;
	}

	protected void createServer() {
		Resource.setDefaultUseCaches(false); // ZipFileClosed exception being caused, this only affects resources loaded from the core war itself

		server = new Server();

		serverHandler = new HandlerList();
		server.setHandler(serverHandler);
	}

	protected void createConnector() {
		HttpConfiguration httpConfig = new HttpConfiguration();

		httpConfig.addCustomizer((connector, channelConfig, request) -> MetaDataHandler.rewriteProxyRequest(request));

		int request_header_size = Integer.getInteger(WEBAPP_REQUEST_HEADER_SIZE, WEBAPP_REQUEST_HEADER_SIZE_DEFAULT);
		int response_header_size = Integer.getInteger(WEBAPP_RESPONSE_HEADER_SIZE, WEBAPP_RESPONSE_HEADER_SIZE_DEFAULT);
		int output_buffer_size = Integer.getInteger(WEBAPP_OUTPUT_BUFFER_SIZE, WEBAPP_OUTPUT_BUFFER_SIZE_DEFAULT);
		int header_cache_size = Integer.getInteger(WEBAPP_HEADER_CACHE_SIZE, WEBAPP_HEADER_CACHE_SIZE_DEFAULT);

		httpConfig.setRequestHeaderSize(request_header_size * HEADER_SIZE_BASE);
		httpConfig.setResponseHeaderSize(response_header_size * HEADER_SIZE_BASE);
		httpConfig.setOutputBufferSize(output_buffer_size * HEADER_SIZE_BASE);
		httpConfig.setHeaderCacheSize(header_cache_size * HEADER_SIZE_BASE);

		ServerConnector connector = new ServerConnector(server,
						new HttpConnectionFactory(httpConfig));

		port = Integer.getInteger(WEBAPP_HTTP_PORT_PROPERTY, WEBAPP_HTTP_PORT_DEFAULT);
		connector.setPort(port);
		server.addConnector(connector);
	}

	protected boolean walkClasspathParentToFind(ClassLoader current) {
		if (getClass().getClassLoader() == current) {
			return true;
		} else if (current == null) {
			return false;
		} else {
			return walkClasspathParentToFind(current.getParent());
		}
	}

	protected void createContext() throws IOException, URISyntaxException {
		String webDefaultXml = WEBDEFAULT_XML;

		context = new WebApplicationSimpleContext(war == null ? "devmode" : war.toString(), System.getProperty(WEBAPP_CONTEXT_PROPERTY, WEBAPP_CONTEXT_DEFAULT));

		context.setClassLoader(this.getClass().getClassLoader());

		if (war == null) {
			// determine if the current class's class loader is a parent of the current thread's class loader.
			// if so, use the current thread, otherwise use the current class

			if (walkClasspathParentToFind(Thread.currentThread().getContextClassLoader())) {
				context.setClassLoader(Thread.currentThread().getContextClassLoader());
			}

			webDefaultXml = WEBDEFAULT_DEV_XML;
		} else if (war.isDirectory()) {
			context.setBaseResource(Resource.newResource(war.toURI().toURL()));
		} else {
			context.setBaseResource(Resource.newResource(new URL("jar:file:" + war.getAbsolutePath() + "!/")));
		}

		context.setConfigurationClasses(getConfigurationClasses(JETTY_CONFIGURATION_CLASSES));

		if (System.getProperty(WEBAPP_WEBDEFAULT_XML_LOCATION) != null) {
			context.setDefaultsDescriptor(System.getProperty(WEBAPP_WEBDEFAULT_XML_LOCATION));
		} else {
			context.setDefaultsDescriptor(webDefaultXml);
		}

		context.setExtractWAR(false);

		// TODO we aren't setting a security handler!!!
		//		context.setSecurityHandler(new RemoteUserSecurityHandler());

		SessionManager sessionManager = context.getSessionHandler().getSessionManager();

		// missing on interface
		if (!(sessionManager instanceof AbstractSessionManager)) {
			throw new RuntimeException("Cannot set secure cookies on session manager.");
		}

		AbstractSessionManager realSessionManager = (AbstractSessionManager) sessionManager;

		boolean allowCookiesToOnlyBePassedSecurely = Boolean.parseBoolean(System.getProperty(WEBAPP_SECURE_COOKIES_PROPERTY, "true"));

		realSessionManager.getSessionCookieConfig().setSecure(allowCookiesToOnlyBePassedSecurely);
		realSessionManager.setHttpOnly(true);

		createContextTempDirectory();
	}

	private String[] getConfigurationClasses(String[] jetty_configuration_classes) {
		String extra = System.getProperty(WEBAPP_EXTRA_CONFIGURATION_CLASSES);
		if (extra != null) {
			List<String> configClasses = Arrays.asList(extra.trim().split(";"));
			configClasses.addAll(Arrays.asList(jetty_configuration_classes));
			return configClasses.toArray(new String[configClasses.size()]);
		} else {
			return jetty_configuration_classes;
		}
	}

	/*
	 * Stats -> MetaData -> Context
	 */
	protected void wrapHandlers() {

		HandlerWrapper wrapper = new MetaDataHandler();
		wrapper.setHandler(context);

		// stats handler keeps count of who is currently using us, so if we still have active connections we can delay shutdown
		statistics = new StatisticsHandler();
		statistics.setHandler(wrapper);

		serverHandler.addHandler(statistics);

	}

	protected void createContextTempDirectory() {
		if (context.getTempDirectory() == null) {
			File tmpDir = new File(System.getProperty("java.io.tmpdir"));

			tmpDir.mkdirs();

			context.setTempDirectory(tmpDir);
		}
	}

	public void run() {
		try {
			createLockFile();

			try {
				start();
				waitForShutdown();
			} finally {
				stop();
			}
		} finally {
			releaseLockFile();
		}
	}

	protected void start() {

		try {
			logger.info("Starting WebApp server");

			createServer();

			createConnector();

			createContext();

			wrapHandlers();

			server.start();

			// Handler/context startup errors aren't propagated, we have to do it manually.
			Throwable error = context.getUnavailableException();

			if (error != null) {
				logger.error("Jetty context startup failed", error);
				throw new RuntimeException("WebApp context startup is unavailable", error);
			}

			if (context.isFailed())
				throw new RuntimeException("WebApp context startup failed");

			logger.info("WebApp server started");
		} catch (Exception e) {
			throw new RuntimeException("WebApp server failed", e);
		}
	}

	protected void stop() {
		if (server == null) {
			logger.error("Never started, can't stop!");
			return;
		}

		attemptCleanClose();

		try {
			logger.info("jetty shutdown: stopping server");

			server.stop();

			logger.info("WebApp server shutdown complete");
		} catch (Exception e) {
			throw new RuntimeException("WebApp server shutdown failed", e);
		} finally {
			statistics = null;
			server = null;
			context = null;
		}
	}

	/*
	 * Attempts a clean close of the connectors and will wait for remaining connections if they are still
	 * open.
	 */
	private void attemptCleanClose() {
		long timeout = Integer.getInteger(WEBAPP_SHUTDOWN_TIMEOUT_PROPERTY, WEBAPP_SHUTDOWN_TIMEOUT_DEFAULT);

		if (timeout > 0) {
			logger.info("jetty shutdown: requesting shutdown");

			try {
				Connector[] connectors = server.getConnectors();
				if (connectors != null) {
					for (Connector connector : connectors) {
						connector.shutdown();
					}
				}

				int open = statistics.getRequestsActive();

				if (open > 0) {
					waitForConnections(timeout, open);
				}
			} catch (Exception e) {
				logger.warn("jetty shutdown: formal shutdown failed", e);
			}
		}
	}

	private void waitForConnections(long timeout, int open) {
		logger.info("jetty shutdown: {} requests are active, delaying for {} ms", open, timeout);

		timeout += System.currentTimeMillis();

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				logger.warn("jetty shutdown: clean shutdown failed sleep interval");
			}

			open = statistics.getRequestsActive();

			if (open <= 0)
				break;

			if (System.currentTimeMillis() >= timeout) {
				logger.warn("jetty shutdown: {} requests not finished, kicking them out", open);
				break;
			}
		}
	}

	protected void createLockFile() {
		String file = System.getProperty(WEBAPP_LOCKFILE_PROPERTY);
		if (file != null && file.length() > 0) {
			lockfile = new WebAppLockFile(file);
			logger.debug("Acquired lock file '{}'", lockfile);
		}
	}

	protected void releaseLockFile() {
		if (lockfile != null) {
			lockfile.release();
			logger.debug("Released lock file '{}'", lockfile);
			lockfile = null;
		}
	}

	protected void waitForShutdown() {
		CountDownLatch latch = new CountDownLatch(1);
		try {
			try {
				Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownWatcher(latch), "shutdown-hook"));
			} catch (IllegalStateException e) {
			}

			if (Boolean.getBoolean(WEBAPP_SHUTDOWN_STDIN_PROPERTY)) {
				Thread stdin = new Thread(new StdinWatcher(latch), "shutdown-stdin");
				stdin.setDaemon(true);
				stdin.start();
			}

			logger.info("WebApp container is up and running on port {}", port);
			try {
				latch.await();
			} catch (InterruptedException e) {
			}
		} finally {
			// Just so the shutdown triggers don't report having triggered
			// a shutdown when it's already happened because of a startup error.
			latch.countDown();
		}
	}
}
