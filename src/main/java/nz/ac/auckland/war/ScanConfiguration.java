package nz.ac.auckland.war;

import nz.ac.auckland.common.scanner.MultiModuleConfigScanner;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.webapp.AbstractConfiguration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;

import java.net.URL;
import java.util.List;

/**
 * author: Richard Vowles - http://gplus.to/RichardVowles
 */
public class ScanConfiguration extends AbstractConfiguration {
  @Override
  public void preConfigure(final WebAppContext context) throws Exception {
    MultiModuleConfigScanner.scan(new MultiModuleConfigScanner.Notifier() {
      @Override
      public void underlayWar(URL url) throws Exception {
      }

      @Override
      public void jar(URL url) throws Exception {
        context.getMetaData().addWebInfJar(Resource.newResource(url));
      }

      @Override
      public void dir(URL url) throws Exception {
      }
    });


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
