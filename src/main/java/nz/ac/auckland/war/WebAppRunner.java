package nz.ac.auckland.war;


import nz.ac.auckland.jetty.RemoteUserSecurityHandler;
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
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
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


	public static final String WEBDEFAULT_XML = "nz/ac/auckland/war/webdefault.xml";
	public static final String WEBDEFAULT_DEV_XML = "nz/ac/auckland/war/webdefault-dev.xml";

	protected static final int WEBAPP_HTTP_PORT_DEFAULT = 8090;
	protected static final int WEBAPP_SHUTDOWN_TIMEOUT_DEFAULT = 12000;
	protected static final String WEBAPP_CONTEXT_DEFAULT = "/";

	// basic required configuration classes in this particular order
	private static String[] JETTY_CONFIGURATION_CLASSES =
		{
			"nz.ac.auckland.war.ScanConfiguration",
			"nz.ac.auckland.war.ScannedWebXmlConfiguration",
			"org.eclipse.jetty.webapp.JettyWebXmlConfiguration"
		};

	private static final Logger log = LoggerFactory.getLogger(WebAppRunner.class);

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

		httpConfig.addCustomizer(new HttpConfiguration.Customizer() {
			@Override
			public void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
				MetaDataHandler.rewriteProxyRequest(request);
			}
		});

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
		context.setSecurityHandler(new RemoteUserSecurityHandler());

//    context.setSecurityHandler(new RemoteUserSecurityHandler());
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
			StringTokenizer st = new StringTokenizer(extra.trim(), ";");

			List<String> configClasses = new ArrayList<>();

			while (st.hasMoreElements()) {
				configClasses.add(st.nextToken());
			}

			for (String configClass : JETTY_CONFIGURATION_CLASSES) {
				configClasses.add(configClass);
			}

			return configClasses.toArray(new String[configClasses.size()]);
		} else {
			return JETTY_CONFIGURATION_CLASSES;
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
			log.info("Starting WebApp server");

			createServer();

			createConnector();

			createContext();

			wrapHandlers();

			server.start();

			// Handler/context startup errors aren't propagated, we have to do it manually.
			Throwable error = context.getUnavailableException();

			if (error != null) {
				log.error("Jetty context startup failed", error);
				throw new RuntimeException("WebApp context startup is unavailable", error);
			}

			if (context.isFailed())
				throw new RuntimeException("WebApp context startup failed");

			log.info("WebApp server started");
		} catch (Exception e) {
			throw new RuntimeException("WebApp server failed", e);
		}
	}

	protected void stop() {
		if (server == null) {
			log.error("Never started, can't stop!");
			return;
		}

		attemptCleanClose();

		try {
			log.info("jetty shutdown: stopping server");

			server.stop();

			log.info("WebApp server shutdown complete");
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
			log.info("jetty shutdown: requesting shutdown");

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
				log.warn("jetty shutdown: formal shutdown failed", e);
			}
		}
	}

	private void waitForConnections(long timeout, int open) {
		log.info("jetty shutdown: {} requests are active, delaying for {} ms", open, timeout);

		timeout += System.currentTimeMillis();

		while (true) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				log.warn("jetty shutdown: clean shutdown failed sleep interval");
			}

			open = statistics.getRequestsActive();

			if (open <= 0)
				break;

			if (System.currentTimeMillis() >= timeout) {
				log.warn("jetty shutdown: {} requests not finished, kicking them out", open);
				break;
			}
		}
	}

	protected void createLockFile() {
		String file = System.getProperty(WEBAPP_LOCKFILE_PROPERTY);
		if (file != null && file.length() > 0) {
			lockfile = new WebAppLockFile(file);
			log.debug("Acquired lock file '{}'", lockfile);
		}
	}

	protected void releaseLockFile() {
		if (lockfile != null) {
			lockfile.release();
			log.debug("Released lock file '{}'", lockfile);
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

			log.info("WebApp container is up and running on port {}", port);
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
