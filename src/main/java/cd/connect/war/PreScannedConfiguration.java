package cd.connect.war;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created 16/08/17. by
 *
 * @author karl
 */
public class PreScannedConfiguration extends AbstractConfiguration {
	public static String RESOURCE_URLS = "cd.connect.jetty.resource-urls";

	private Logger logger = LoggerFactory.getLogger(getClass());

	protected static Resource webXml;

	public PreScannedConfiguration(){

	}

	@SuppressWarnings("unchecked")
	@Override
	public void configure(WebAppContext context) throws Exception {
		List<Resource> resources = (List<Resource>) context.getAttribute(RESOURCE_URLS);

		if (resources != null) {
			if (resources.size() > 1) {
				List<Resource> temp = new ArrayList<>();
				temp.add(context.getBaseResource());
				temp.addAll(resources);
				context.setBaseResource( new ResourceCollection( temp.toArray( new Resource[ temp.size() ] ) ) );
			} else {
				context.setBaseResource( resources.get( 0 ) );
			}
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void preConfigure(final WebAppContext context) throws Exception {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(WebAppRunner.PRE_SCANNED_RESOURCE_NAME)));
			String line = null;
			while ((line = br.readLine()) != null) {
				String[] values = line.split(",");
				if( values.length == 2 ){
					String resourceName = values[ 0 ];
					String offsetURL = values[ 1 ];
					try {
						if ("WEB-INF/web.xml".equals(resourceName)) {
							foundWebXml(resourceName, offsetURL, context);
							if (context.getBaseResource() == null) {
								if (logger.isDebugEnabled()) {
									logger.debug("webapp.scan: found base directory {}", offsetURL );
								}
								context.setBaseResource(Resource.newResource(offsetURL));  // add base directory
							}
						} else if ("META-INF/resources/WEB-INF/web.xml".equals(resourceName)) {
							foundWebXml(resourceName, offsetURL, context);
						} else if ("META-INF/web-fragment.xml".equals(resourceName)) {
							// don't worry about adding the resource as it may not even be there
							URL resolvedUrl = resolvedUrl(resourceName, offsetURL);
							if (logger.isDebugEnabled()) {
								logger.debug("webapp.scan: found web fragment {}", resolvedUrl.toString());
							}
							Resource fragmentResource = Resource.newResource(offsetURL);
							context.getMetaData().addWebInfJar(fragmentResource);
							context.getMetaData().addFragment(fragmentResource, Resource.newResource(resolvedUrl));
						}
					} catch (MalformedURLException mue) {
						logger.warn( "failed to process `{}`. may not wire up", line );
					}
				}
				// use line here
			}
		} catch (IOException ioe) {
			logger.debug("{} not present falling through to do classpath scanning", WebAppRunner.PRE_SCANNED_RESOURCE_NAME);
		}

	}

	protected void foundWebXml( String resourceName , String offsetURL, WebAppContext context ) throws Exception {
		URL url = resolvedUrl( resourceName, offsetURL );
		if (context.getMetaData().getWebXml() == null) {
			webXml = Resource.newResource( url );
			if (logger.isDebugEnabled()) {
				logger.debug("webapp.scan: found web.xml {}", webXml.toString());
			}
			context.getMetaData().setWebXml(webXml);
		} else {
			logger.info( "Found extra web.xml, ignoring {}", url.toString() );
		}
	}

	public URL resolvedUrl( String resourceName , String offsetURL ) {
		String url = !offsetURL.contains("!/") ? offsetURL = "jar:" + offsetURL + "!" : offsetURL;
		try {
			return new URL(url + (url.endsWith("/")?"":"/") + resourceName);
		} catch (MalformedURLException e) {
			throw new RuntimeException("Failed to convert url to offset URL :" + offsetURL, e);
		}
	}

}
