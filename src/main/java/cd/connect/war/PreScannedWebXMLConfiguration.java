package cd.connect.war;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import java.io.IOException;

public class PreScannedWebXMLConfiguration extends WebXmlConfiguration {

	@Override
	protected Resource findWebXml(WebAppContext genericContext) throws IOException {
		return PreScannedConfiguration.webXml;
	}

}
