package cd.connect.war;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;

import java.io.IOException;
import java.io.InputStream;

/**
 * Treats all classes as "non server classes".  Allows us to override security and various other things
 * we need to do to get this working.
 */
public class WebApplicationSimpleContext extends WebAppContext {
	private static final Logger logger = Log.getLogger(WebApplicationSimpleContext.class);

  public WebApplicationSimpleContext(String webApp, String contextPath) {
    super(webApp, contextPath);
	  _scontext = new MyContextHandler();
  }

  @Override
  public boolean isServerClass(String name) {
    return false;
  }

	public class MyContextHandler extends Context {
		private final boolean devMode;

		public MyContextHandler() {
			devMode = System.getProperty(WebAppRunner.WEBAPP_WAR_FILENAME) == null;
		}

		@Override
		public InputStream getResourceAsStream(String path) {
			if (devMode) {
				return super.getResourceAsStream(path.replace("//", "/")); // jawr nonsense
			}

			try {
				Resource resource = WebApplicationSimpleContext.this.getResource(path);

				if (resource != null && resource.exists()) {
					return resource.getInputStream();
				}
			} catch (IOException e) {
				logger.ignore(e);
			}

			return null;
		}
	}
}
