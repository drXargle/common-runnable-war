package nz.ac.auckland.war;

import com.bluetrainsoftware.classpathscanner.ClasspathScanner;
import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.MetaData;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans the classpath for all of the important components we need - web fragments, web resource directories
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class ScanConfiguration extends AbstractConfiguration {
	private Logger log = LoggerFactory.getLogger(getClass());

	private static final String META_INF_RESOURCES_WEB_INF_WEB_XML = "META-INF/resources/WEB-INF/web.xml";

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
					if (scanResource.resourceName.equals("WEB-INF/web.xml")) {
						foundWebXml(scanResource, context);

						if (context.getBaseResource() == null) {
								context.setBaseResource(Resource.newResource(scanResource.offsetUrl));  // add base directory
						}
					} else if (scanResource.resourceName.equals(META_INF_RESOURCES_WEB_INF_WEB_XML)) {
							// need to add offseturl + /META-INF/resources
						Resource base = Resource.newResource(scanResource.newOffset("META-INF/resources"));
						context.setBaseResource(base);
					} else if (scanResource.resourceName.equals("META-INF/web-fragment.xml")) {
						// don't worry about adding the resource as it may not even be there
						context.getMetaData().addFragment(Resource.newResource(scanResource.offsetUrl),
							Resource.newResource(scanResource.getResolvedUrl()));
					} else if (isWebResourceBase(scanResource)) {
						resources.add(Resource.newResource(scanResource.getResolvedUrl()));
					}
				}
				return null;
			}

			@Override
			public void deliver(ScanResource desire, InputStream inputStream) {
			}

			@Override
			public boolean isInteresting(URL url) {
				return true;
			}

			@Override
			public boolean removeListenerOnScanCompletion() {
				return true;
			}
		};

		ClasspathScanner scanner = ClasspathScanner.getInstance();

		scanner.registerResourceScanner(listener);
		scanner.scan(context.getClassLoader());
  }

	protected void foundWebXml(ResourceScanListener.ScanResource scanResource, WebAppContext context) throws Exception {
		if (context.getMetaData().getWebXml() == null) {
			webXml = Resource.newResource(scanResource.getResolvedUrl());
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
	 * @param scanResource
	 * @return
	 */
	protected boolean isWebResourceBase(ResourceScanListener.ScanResource scanResource) {
		return scanResource.resourceName.equals("META-INF/resource") ||
			(scanResource.file == null && scanResource.offsetUrl.toString().endsWith("!WEB-INF/classes/")) ||
			(scanResource.file != null && scanResource.file.isDirectory() &&
				( scanResource.file.getAbsolutePath().endsWith("/src/main/webapp") ||
				scanResource.file.getAbsolutePath().endsWith("/src/test/webapp")  ));
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
