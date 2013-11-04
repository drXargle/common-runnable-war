package nz.ac.auckland.war;


import nz.ac.auckland.common.scanner.MultiModuleConfigScanner;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.FragmentConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class DevConfiguration extends WebXmlConfiguration {
  private static final Logger log = LoggerFactory.getLogger(DevConfiguration.class);

	public static final String DEV_CONFIGURATION_EXTRA_WEBXML = DevConfiguration.class.getName() + ".webxml";
	public static final String DEV_CONFIGURATION_EXTRA_WEBDIRS = DevConfiguration.class.getName() + ".webdirs";

  public void addResource(WebAppContext context, String attribute, Resource jar) {
    @SuppressWarnings("unchecked")
    List<Resource> list = (List<Resource>) context.getAttribute(attribute);
    if (list == null) {
      list = new ArrayList<>();
      context.setAttribute(attribute, list);
    }
    if (!list.contains(jar))
      list.add(jar);
  }

  /**
   * this has to happen before the configuration's are run, otherwise the classpath isn't added properly.
   *
   * @param context - the web aplication context we need to push discovered data info
   * @throws IOException
   */
  public static void addExtraClasspath(WebAppContext context) throws IOException {
    final List<String> underlays = new ArrayList<>();

    try {
      MultiModuleConfigScanner.scan(new MultiModuleConfigScanner.Notifier() {
        @Override
        public void underlayWar(URL url) throws Exception {
          String path = url.toURI().getPath();

          JarFile jar = new JarFile(path);

          if (jar.getEntry("WEB-INF/classes") != null) {
            underlays.add("jar:file:" + path + "!/WEB-INF/classes");
          }

          jar.close();
        }

        @Override
        public void jar(URL url) throws Exception {
        }

        @Override
        public void dir(URL url) throws Exception {
        }
      });
    } catch (Exception ex) {
      throw new IOException("Failed to scan for underlays", ex);
    }

    if (underlays.size() > 0) {
      StringBuilder sb = new StringBuilder();
      for (String underlay : underlays) {
        if (sb.length() > 0) {
          sb.append(";");
        }

        sb.append(underlay);
      }

      log.info("classpath has underlays, adding extra classpath {}", sb.toString());

      context.setExtraClasspath(sb.toString());
    }


  }

  @Override
  protected Resource findWebXml(WebAppContext genericContext) throws IOException {
    final List<Resource> resources = new ArrayList<>();

    final WebApplicationDevContext context = (WebApplicationDevContext) genericContext;


    try {
      MultiModuleConfigScanner.scan(new MultiModuleConfigScanner.Notifier() {
        @Override
        public void underlayWar(URL url) throws Exception {
          JarFile jar = new JarFile(url.toURI().getPath());

          if (jar.getEntry("WEB-INF/web.xml") != null) {
            log.info("devmode: detected web.xml in " + url.toString());
            resources.add(Resource.newResource(new URL("jar:file:" + url.toURI().getPath() + "!/WEB-INF/web.xml")));

            if (context.getBaseResource() == null) {
              context.setBaseResource(Resource.newResource("jar:file:" + url.toURI().getPath() + "!/"));
            }
          }

          if (jar.getEntry("WEB-INF/classes") != null) {
            addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource("jar:file:" + url.toURI().getPath()));
          }

          jar.close();
        }

        @Override
        public void jar(URL url) throws Exception {
          JarFile jar = new JarFile(url.toURI().getPath());
          if (jar.getEntry("META-INF/web-fragment.xml") != null) {
            log.info("devmode: found fragment: {}", url.getPath());
//          ExposeJarFileResource res = new ExposeJarFileResource(new URL("jar:file:" +path + "!/"));
            Resource res = Resource.newResource(url.toURI().getPath());
            addResource(context, FragmentConfiguration.FRAGMENT_RESOURCES, res);
            context.getMetaData().addWebInfJar(res);
          } else if (jar.getEntry("META-INF/resources") != null) {   // Servlet 3 Jar
            context.getMetaData().addWebInfJar(Resource.newResource(url.toURI().getPath()));

            if (jar.getEntry("META-INF/resources/WEB-INF/web.xml") != null) {
              log.info("devmode: found web.xml in {}", url.toURI().getPath());
              context.setBaseResource(Resource.newResource("jar:file:" + url.toURI().getPath() + "!/META-INF/resources/"));
              resources.add(Resource.newResource("jar:file:" + url.toURI().getPath() + "!/META-INF/resources/WEB-INF/web.xml"));

            }
          }

          jar.close();
        }

        @Override
        public void dir(URL url) throws Exception {
          File base = new File(url.toURI());
          File webXml = new File(base, "WEB-INF/web.xml");

          if (webXml.exists()) {
            log.info("devmode: web.xml: {}", webXml.getAbsolutePath());
            resources.add(Resource.newResource(webXml));
            context.setBaseResource(Resource.newResource(base));
          }

          webXml = new File(base, "META-INF/web-fragment.xml");

          if (webXml.exists()) {
            log.info("devmode: found fragment: {}", base.getAbsolutePath());
            Resource fakeWebInfJar = Resource.newResource(base);
            addResource(context, FragmentConfiguration.FRAGMENT_RESOURCES, fakeWebInfJar);
            context.getMetaData().addWebInfJar(fakeWebInfJar);
          } else if (base.getAbsolutePath().endsWith("webapp")) {
            addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(base));

            scssCheck(base, base, context, true);
          }

          File metaInfResources = new File(base, "META-INF/resources");

          if (metaInfResources.exists()) {
            addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(metaInfResources));

            scssCheck(metaInfResources, base, context, false);
          }
        }
      });
    } catch (Exception ex) {
      throw new IOException("Bad attempt at finding XML", ex);
    }

	  String tokenize = System.getProperty(DEV_CONFIGURATION_EXTRA_WEBXML);
	  if (tokenize != null) {
		  for(String webxml : tokenize.split(";")) {
			  resources.add(Resource.newResource(new File(webxml)));
		  }
	  }

	  tokenize = System.getProperty(DEV_CONFIGURATION_EXTRA_WEBDIRS);
	  if (tokenize != null) {
		  for(String webdir : tokenize.split(";")) {
			  if (context.getBaseResource() != null) {
				  addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(new File(webdir)));
			  } else {
				  context.setBaseResource(Resource.newResource(new File(webdir)));
			  }
		  }
	  }

    if (resources.size() == 0)
      log.error("No web.xml file found on classpath");


    return resources.size() > 0 ? resources.get(0) : null;
  }

  /**
   * determines if this source path (which all directories are) is a Web resources directory and this should have CSS
   *
   * @param projBase - the base directory for the project
   * @param context - the web application context
   * @throws IOException
   */
  private void scssCheck(File base, File projBase, WebAppContext context, boolean webapp) throws IOException {
    if (new File(base, "scss").exists()) {
      File projDir = projBase.getParentFile().getParentFile().getParentFile();

      MultiModuleConfigScanner.GroupArtifactVersion gav = MultiModuleConfigScanner.classpathGavs.get(projDir.getAbsolutePath());

      if (gav == null)
        throw new RuntimeException("SCSS/SASS directory found at " + base.getAbsolutePath() + " but cannot find pom.xml for match");

      File cssDir;

      if (webapp)
        cssDir = new File(projDir, "target/" + gav.artfiactId + "-" + gav.version + "/css");
      else
        cssDir = new File(projDir, "target/classes/META-INF/resources/css");

      if (!cssDir.exists()) {
        throw new RuntimeException("SCSS/SASS directory found at " + base.getAbsolutePath() + " but no matching CSS directory found at " +
          cssDir.getAbsolutePath() + " - please ensure you are running \"mvn sass:watch\" or \"mvn process-resources\" for each open war underlay.");
      }

      addResource(context, WebInfConfiguration.RESOURCE_URLS, Resource.newResource(cssDir.getParentFile()));

    }

  }

  @Override
  public void configure(WebAppContext context) throws Exception {
    super.configure(context);

    // taken from WebInfConfiguration
    List<Resource> resources = (List<Resource>) context.getAttribute(WebInfConfiguration.RESOURCE_URLS);

    if (resources != null) {
      Resource[] collection = new Resource[resources.size() + 1];
      int i = 0;
      collection[i++] = context.getBaseResource();

      for (Resource resource : resources)
        collection[i++] = resource;

      if (log.isDebugEnabled()) {
        StringBuilder sb = new StringBuilder("devmode: resource base paths include: \n");
        for(Resource r : resources) {
          sb.append(r.toString());
          sb.append("\n");
        }

        log.debug(sb.toString());
      }

      context.setBaseResource(new ResourceCollection(collection));
    }

  }
}
