package nz.ac.auckland.war;

import com.bluetrainsoftware.classpathscanner.ClasspathScanner;
import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Scans the classpath for all of the important components we need - web fragments, web resource directories
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class ScanConfiguration extends AbstractConfiguration {
	private Logger log = LoggerFactory.getLogger(getClass());

	public static Resource webXml;

	protected static final Path PATH_TARGET_RESOURCES = Paths.get("target/classes/META-INF/resources");
	protected static final Path PATH_TARGET_TEST_RESOURCES = Paths.get("target/test-classes/META-INF/resources");

	public static String RESOURCE_URLS = "nz.ac.auckland.jetty.resource-urls";

	private final boolean devMode;

	public ScanConfiguration() {
		devMode = System.getProperty(WebAppRunner.WEBAPP_WAR_FILENAME) == null;
	}



	@SuppressWarnings("unchecked")
	@Override
	public void preConfigure(final WebAppContext context) throws Exception {
		List<Resource> theResources = (List<Resource>) context.getAttribute(RESOURCE_URLS);

		if (theResources == null) {
			theResources = new ArrayList<>();
			context.setAttribute(RESOURCE_URLS, theResources);
		}

		final List<Resource> resources = theResources;
		final List<ResourceScanListener.ScanResource> interesting = new ArrayList<>();

		final InMemoryResource inMemoryResource = devMode ? null : new InMemoryResource();

		if (inMemoryResource != null) {
			resources.add(inMemoryResource);
		}

		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
				interesting.clear();

				for (ScanResource scanResource : scanResources) {
					if ("WEB-INF/web.xml".equals(scanResource.resourceName)) {
						foundWebXml(scanResource, context);

						if (context.getBaseResource() == null) {
							context.setBaseResource(Resource.newResource(scanResource.offsetUrl));  // add base directory
						}
					} else if ("META-INF/resources/WEB-INF/web.xml".equals(scanResource.resourceName)) {
						// need to add offseturl + /META-INF/resources
						foundWebXml(scanResource, context);
					} else if ("META-INF/web-fragment.xml".equals(scanResource.resourceName)) {
						// don't worry about adding the resource as it may not even be there
						URL resolvedUrl = scanResource.getResolvedUrl();

						if (log.isDebugEnabled()) {
							log.debug("webapp.scan: found web fragment {}", resolvedUrl.toString());
						}

						Resource fragmentResource = Resource.newResource(scanResource.offsetUrl);

						context.getMetaData().addWebInfJar(fragmentResource);
						context.getMetaData().addFragment(fragmentResource, Resource.newResource(resolvedUrl));
					} else if (devMode && isWebResourceBase(scanResource)) {
						URL resolvedUrl = morphDevelopmentResource(scanResource);

						if (log.isDebugEnabled()) {
							log.debug("webapp.scan: found resource {}", resolvedUrl.toString());
						}
						resources.add(Resource.newResource(resolvedUrl));
					} else if (!devMode && prefixWebResource(scanResource) != null) {
						interesting.add(scanResource);
					}
				}

				return interesting;
			}

			@Override
			public void deliver(ScanResource desire, InputStream inputStream) {
				// this should only ever happen in production mode

				putResource(desire, prefixWebResource(desire), inputStream, inMemoryResource);
			}

			@Override
			public InterestAction isInteresting(InterestingResource interestingResource) {
				String url = interestingResource.url.toString();
				if (url.contains("jre") || url.contains("jdk")) {
					return InterestAction.NONE;
				} else {
					return InterestAction.ONCE;
				}
			}

			@Override
			public void scanAction(ScanAction action) {
				if (action == ScanAction.COMPLETE) {
					context.getMetaData().orderFragments();
				}
			}
		};

		ClasspathScanner scanner = ClasspathScanner.getInstance();

		scanner.registerResourceScanner(listener);
		scanner.scan(context.getClassLoader());
	}

	protected void putResource(ResourceScanListener.ScanResource desire, String stripPrefix, InputStream stream, InMemoryResource resource) {
		String resourceName = desire.resourceName;

		if (stripPrefix.length() > 0) {
			resourceName = resourceName.substring(stripPrefix.length());
		}

		String[] paths = resourceName.split("/");
		for(int count = 0; count < paths.length - 1; count ++) {
			InMemoryResource child = resource.findPath(paths[count]);

			if (child == null) {
				child = resource.addDirectory(paths[count]);
			}

			resource = child;
		}

		if (desire.entry.isDirectory()) {
			resource.addDirectory(paths[paths.length-1]);
		} else {
			resource.addFile(paths[paths.length-1], stream);
		}
	}

	protected String prefixWebResource(ResourceScanListener.ScanResource scanResource) {
		if (scanResource.resourceName.startsWith("META-INF/resources/")) {
			return "META-INF/resources/";
		}

		return null;
	}

	protected void foundWebXml(ResourceScanListener.ScanResource scanResource, WebAppContext context) throws Exception {
		if (context.getMetaData().getWebXml() == null) {
			webXml = Resource.newResource(scanResource.getResolvedUrl());

			if (log.isDebugEnabled()) {
				log.debug("webapp.scan: found web.xml {}", webXml.toString());
			}

			context.getMetaData().setWebXml(webXml);
		} else {
			log.info("Found extra web.xml, ignoring {}", scanResource.getResolvedUrl().toString());
		}
	}

	/**
	 * if the resource is
	 * (1) called META-INF/resource or
	 * (2) it is a directory and it ends with src/main|test/webapp
	 * (3) the offset url ends with WEB-INF/classes/
	 *
	 * @param scanResource - the url class path that is being made available
	 * @return whether it is a web resource
	 */
	protected boolean isWebResourceBase(ResourceScanListener.ScanResource scanResource) {
		return scanResource.resourceName.equals("META-INF/resources") ||
				scanResource.resourceName.equals("META-INF/resources/") ||
				(scanResource.file == null && scanResource.offsetUrl.toString().endsWith("!WEB-INF/classes/")) ||
				(scanResource.file != null && scanResource.file.isDirectory() &&
						(scanResource.file.getAbsolutePath().endsWith("/src/main/webapp") ||
								scanResource.file.getAbsolutePath().endsWith("/src/test/webapp")));
	}

	protected URL morphDevelopmentResource(ResourceScanListener.ScanResource scanResource) {
		URL resolved = scanResource.getResolvedUrl();

		if (scanResource.file != null && scanResource.file.isDirectory()) {
			Path absoluteFilePath = scanResource.file.toPath();
			try {
				if (absoluteFilePath.endsWith(PATH_TARGET_TEST_RESOURCES)) {
					resolved = new File(scanResource.file.getParentFile().getParentFile().getParentFile().getParentFile(), "src/test/resources/META-INF/resources").toURI().toURL();
				} else if (absoluteFilePath.endsWith(PATH_TARGET_RESOURCES)) {
					resolved = new File(scanResource.file.getParentFile().getParentFile().getParentFile().getParentFile(), "src/main/resources/META-INF/resources").toURI().toURL();
				}
			} catch (MalformedURLException mue) {
				log.error("Unable to morph {} to development resource, this is unexpected, be warned!", resolved.toString());
			}
		}

		return resolved;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void configure(WebAppContext context) throws Exception {

		// taken from WebInfConfiguration
		List<Resource> resources = (List<Resource>) context.getAttribute(RESOURCE_URLS);

		if (resources != null) {
			if (resources.size() > 1) {
				Resource[] collection = new Resource[resources.size() + 1];
				int i = 0;
				collection[i++] = context.getBaseResource();
				for (Resource resource : resources)
					collection[i++] = resource;
				context.setBaseResource(new ResourceCollection(collection));
			} else {
				context.setBaseResource(resources.get(0));
			}
		}
	}
}
