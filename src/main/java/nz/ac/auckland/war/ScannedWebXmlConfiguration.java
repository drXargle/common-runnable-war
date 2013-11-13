package nz.ac.auckland.war;


import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


public class ScannedWebXmlConfiguration extends WebXmlConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ScannedWebXmlConfiguration.class);

	public static final String DEV_CONFIGURATION_EXTRA_WEBXML = "webapp.webxml";
	public static final String DEV_CONFIGURATION_EXTRA_WEBDIRS = "webapp.webdirs";

  @Override
  protected Resource findWebXml(WebAppContext genericContext) throws IOException {
	  return ScanConfiguration.webXml;
  }

//
//        @Override
//        public void dir(URL url) throws Exception {
//          File base = new File(url.toURI());
//          File webXml = new File(base, "WEB-INF/web.xml");
//
//          if (webXml.exists()) {
//            log.info("devmode: web.xml: {}", webXml.getAbsolutePath());
//            resources.add(Resource.newResource(webXml));
//            context.setBaseResource(Resource.newResource(base));
//          }
//
//          webXml = new File(base, "META-INF/web-fragment.xml");
//
//          if (webXml.exists()) {
//            log.info("devmode: found fragment: {}", base.getAbsolutePath());
//            Resource fakeWebInfJar = Resource.newResource(base);
//            addResource(context, FragmentConfiguration.FRAGMENT_RESOURCES, fakeWebInfJar);
//            context.getMetaData().addWebInfJar(fakeWebInfJar);
//          } else if (base.getAbsolutePath().endsWith("webapp")) {
//            addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(base));
//
//            scssCheck(base, base, context, true);
//          }
//
//          File metaInfResources = new File(base, "META-INF/resources");
//
//          if (metaInfResources.exists()) {
//            addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(metaInfResources));
//
//            scssCheck(metaInfResources, base, context, false);
//          }
//        }
//      });
//    } catch (Exception ex) {
//      throw new IOException("Bad attempt at finding XML", ex);
//    }
//
//	  String tokenize = System.getProperty(DEV_CONFIGURATION_EXTRA_WEBXML);
//	  if (tokenize != null) {
//		  for(String webxml : tokenize.split(";")) {
//			  resources.add(Resource.newResource(new File(webxml)));
//		  }
//	  }
//
//	  tokenize = System.getProperty(DEV_CONFIGURATION_EXTRA_WEBDIRS);
//	  if (tokenize != null) {
//		  for(String webdir : tokenize.split(";")) {
//			  if (context.getBaseResource() != null) {
//				  addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(new File(webdir)));
//			  } else {
//				  context.setBaseResource(Resource.newResource(new File(webdir)));
//			  }
//		  }
//	  }
//
//    if (resources.size() == 0)
//      log.error("No web.xml file found on classpath");
//
//
//    return resources.size() > 0 ? resources.get(0) : null;
//  }
//
//  /**
//   * determines if this source path (which all directories are) is a Web resources directory and this should have CSS
//   *
//   * @param projBase - the base directory for the project
//   * @param context - the web application context
//   * @throws IOException
//   */
//  private void scssCheck(File base, File projBase, WebAppContext context, boolean webapp) throws IOException {
//    if (new File(base, "scss").exists()) {
//      File projDir = projBase.getParentFile().getParentFile().getParentFile();
//
//      MultiModuleConfigScanner.GroupArtifactVersion gav = MultiModuleConfigScanner.classpathGavs.get(projDir.getAbsolutePath());
//
//      if (gav == null)
//        throw new RuntimeException("SCSS/SASS directory found at " + base.getAbsolutePath() + " but cannot find pom.xml for match");
//
//      File cssDir;
//
//      if (webapp)
//        cssDir = new File(projDir, "target/" + gav.artfiactId + "-" + gav.version + "/css");
//      else
//        cssDir = new File(projDir, "target/classes/META-INF/resources/css");
//
//      if (!cssDir.exists()) {
//        throw new RuntimeException("SCSS/SASS directory found at " + base.getAbsolutePath() + " but no matching CSS directory found at " +
//          cssDir.getAbsolutePath() + " - please ensure you are running \"mvn sass:watch\" or \"mvn process-resources\" for each open war underlay.");
//      }
//
//      addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(cssDir.getParentFile()));
//
//    }
//
//  }


}
