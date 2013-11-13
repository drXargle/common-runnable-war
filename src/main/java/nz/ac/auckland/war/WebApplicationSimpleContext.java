package nz.ac.auckland.war;

import org.eclipse.jetty.webapp.WebAppContext;

/**
 * Treats all classes as "non server classes".  Allows us to override security and various other things
 * we need to do to get this working.
 */
public class WebApplicationSimpleContext extends WebAppContext {

  public WebApplicationSimpleContext(String webApp, String contextPath) {
    super(webApp, contextPath);
  }

  @Override
  public boolean isServerClass(String name) {
    return false;
  }
}
