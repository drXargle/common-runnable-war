package nz.ac.auckland.war;

import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class WebApplicationDevContext extends WebAppContext {

  private static final Logger log = LoggerFactory.getLogger(WebApplicationDevContext.class);

  private List<String> paths = new ArrayList<String>();


  public WebApplicationDevContext(String webApp, String contextPath) {
    super(webApp, contextPath);
  }


  @Override
  public boolean isServerClass(String name) {
    return false;
  }
}
