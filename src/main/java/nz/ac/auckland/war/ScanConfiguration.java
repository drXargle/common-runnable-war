package nz.ac.auckland.war;

import com.bluetrainsoftware.classpathscanner.ClasspathScanner;
import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans the classpath for all of the important components we need - web fragments, web resource directories
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class ScanConfiguration extends AbstractConfiguration {
	private Logger log = LoggerFactory.getLogger(getClass());

	public static Resource webXml;

	@Override
	public void preConfigure(final WebAppContext context) throws Exception {
		List<Resource> theResources = (List<Resource>) context.getAttribute(WebInfConfiguration.RESOURCE_URLS);

		if (theResources == null) {
			theResources = new ArrayList<>();
			context.setAttribute(WebInfConfiguration.RESOURCE_URLS, theResources);
		}

		final List<Resource> resources = theResources;

		ResourceScanListener listener = new ResourceScanListener() {
			@Override
			public List<ScanResource> resource(List<ScanResource> scanResources) throws Exception {
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
					} else if (isWebResourceBase(scanResource)) {
						URL resolvedUrl = morphDevelopmentResource(scanResource);

						if (log.isDebugEnabled()) {
							log.debug("webapp.scan: found resource {}", resolvedUrl.toString());
						}
						resources.add(Resource.newResource(resolvedUrl));
					}
				}
				return null;
			}

			@Override
			public void deliver(ScanResource desire, InputStream inputStream) {
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
	 * @param scanResource
	 * @return
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
			try {
				if (scanResource.file.getPath().endsWith("/target/test-classes/META-INF/resources")) {
					resolved = new File(scanResource.file.getParentFile().getParentFile().getParentFile().getParentFile(), "src/test/resources/META-INF/resources").toURI().toURL();
				} else if (scanResource.file.getPath().endsWith("/target/classes/META-INF/resources")) {
					resolved = new File(scanResource.file.getParentFile().getParentFile().getParentFile().getParentFile(), "src/main/resources/META-INF/resources").toURI().toURL();
				}
			} catch (MalformedURLException mue) {
				log.error("Unable to morph {} to development resource, this is unexpected, be warned!", resolved.toString());
			}
		}

		return resolved;
	}

	@Override
	public void configure(WebAppContext context) throws Exception {

		// taken from WebInfConfiguration
		List<Resource> resources = (List<Resource>) context.getAttribute(WebInfConfiguration.RESOURCE_URLS);

		if (resources != null) {
			Resource[] collection = new Resource[resources.size() + 1];
			int i = 0;
			collection[i++] = context.getBaseResource();
			for (Resource resource : resources)
				collection[i++] = resource;
			context.setBaseResource(new ResourceCollection(collection));
		}
	}
}
