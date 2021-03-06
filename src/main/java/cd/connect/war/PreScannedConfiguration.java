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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PreScannedConfiguration extends AbstractConfiguration {
	public static String RESOURCE_URLS = "cd.connect.jetty.resource-urls";

	private Logger logger = LoggerFactory.getLogger(getClass());

	protected String applicationRoot;

	protected static Resource webXml;

	public PreScannedConfiguration(){
		try {
			applicationRoot = System.getProperty(WebAppRunner.WEBAPP_WAR_FILENAME, this.getClass().getResource("/").toURI().toString());
		} catch (URISyntaxException e) {
//
		}

		logger.debug( "Resource root is `{}`", applicationRoot );
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
		List<Resource> theResources = (List<Resource>) context.getAttribute(RESOURCE_URLS);

		if (theResources == null) {
			theResources = new ArrayList<>();
			context.setAttribute(RESOURCE_URLS, theResources);
		}

		final List<Resource> resources = theResources;

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream(WebAppRunner.getPreScanConfigProperty())));
			String line;
			while ((line = br.readLine()) != null) {
				logger.debug( "processing `{}`", line );
				if( line.contains( "=" ) ){
					String[] values = line.split("=");
					String key = values[ 0 ].toLowerCase();
					URL url = resolvedUrl( values[ 1 ] );
					switch ( key ){
						case "webxml":
							String parent = values[ 1 ].replace( "WEB-INF/web.xml","" );
							Resource resource = Resource.newResource( url );
							if ( context.getMetaData().getWebXml() == null ) {
								webXml = resource;
								logger.debug( "Setting web.xml from {}", url.toString() );
								context.getMetaData().setWebXml( webXml );
							} else {
								logger.info( "web.xml already set, ignoring {}", url.toString() );
							}

							if( parent.endsWith( "file:/" ) || parent.endsWith( "!/" ) ) {
								// so it is in the root
								if ( context.getBaseResource() == null ) {
									logger.debug( "set base resource {}", url.toString() );
									context.setBaseResource( resource );  // add base resource
								}
							}
							resources.add( Resource.newResource( resolvedUrl( parent ) ) );
							break;

						case "fragment":
							logger.debug( "included web fragment {}", url.toString() );
							String resourceURL = url.toString();
							int bang = resourceURL.lastIndexOf( '!' );
							if( bang > 0 ){
								// get rid of the 'jar:' and everything from the '!' onwards
								resourceURL = resourceURL.substring( 4, bang );
							}

							Resource fragmentResource = Resource.newResource( new URL( resourceURL ) );
							context.getMetaData().addWebInfJar( fragmentResource );
							context.getMetaData().addFragment( fragmentResource, Resource.newResource( url ) );
							// we shouldn't be adding any resource here as `META-INF/resource/` will show up as
							// it's own resource= line...
							break;

						case "resource":
							logger.debug( "added resource {}", url.toString() );
							resources.add( Resource.newResource( url ) );
							break;
					}
				}
			}
		} catch ( Exception ex ) {
			logger.warn( "Problems loading {}", WebAppRunner.getPreScanConfigProperty(), ex );
			throw new RuntimeException( "Failed to load the prescan class mappings from " + WebAppRunner.getPreScanConfigProperty() );
		}

	}

	private URL resolvedUrl( String url ) throws MalformedURLException{
		String full = url.replaceFirst( "file:/", applicationRoot );
		try {
			return new URL( full );
		} catch ( MalformedURLException mue ) {
			logger.warn( "Malformed URL detected : {}", full );
			throw mue; // rethrow
		}
	}

}
