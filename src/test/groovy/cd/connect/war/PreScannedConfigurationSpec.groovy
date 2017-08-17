package cd.connect.war

import org.eclipse.jetty.webapp.WebAppContext
import spock.lang.Specification

class PreScannedConfigurationSpec extends Specification {

	void "Only have a web.xml in the root"() {
		given: "we have a configuration scanner and some resources"
			System.setProperty( WebAppRunner.WEBAPP_PRE_SCANNED_RESOURCE_NAME, '/web_inf_webXML')
			PreScannedConfiguration cfg = new PreScannedConfiguration()
			WebAppContext context = new WebAppContext( classLoader: this.class.classLoader )

		when: "we scan"
			cfg.preConfigure( context )

		then:
			context.metaData.webXml.getResource().getURI().toString() == cfg.applicationRoot + 'WEB-INF/web.xml'
	}

	void "Only have a web.xml in META-INF/resources"() {
		given: "we have a configuration scanner and some resources"
			System.setProperty( WebAppRunner.WEBAPP_PRE_SCANNED_RESOURCE_NAME, '/meta_inf_webXML')
			PreScannedConfiguration cfg = new PreScannedConfiguration()
			WebAppContext context = new WebAppContext( classLoader: this.class.classLoader )

		when: "we scan"
			cfg.preConfigure( context )

		then:
			context.metaData.webXml.getResource().getURI().toString() == cfg.applicationRoot + 'META-INF/resources/WEB-INF/web.xml'
	}

	void "web.xml in both root and in META-INF/resources"() {
		given: "we have a configuration scanner and some resources"
			System.setProperty( WebAppRunner.WEBAPP_PRE_SCANNED_RESOURCE_NAME, '/both_webXML')
			PreScannedConfiguration cfg = new PreScannedConfiguration()
			WebAppContext context = new WebAppContext()

		when: "we scan"
			cfg.preConfigure( context )

		then:
			context.metaData.webXml.getResource().getURI().toString() == cfg.applicationRoot + 'WEB-INF/web.xml'
	}

	void "we can connect with darth jarjar"() {
		given: "we have a configuration scanner and some resources"
			System.setProperty( WebAppRunner.WEBAPP_PRE_SCANNED_RESOURCE_NAME, '/jarjar_binks')
			PreScannedConfiguration cfg = new PreScannedConfiguration()
			WebAppContext context = new WebAppContext( classLoader: this.class.classLoader )

		when: "we scan"
			cfg.preConfigure( context )

		then:
			context.metaData.webXml.getResource().getURI().toString() == "jar:${cfg.applicationRoot}jars/binks.jar!/WEB-INF/web.xml"
	}

	void "web fragment"() {
		given: "we have a configuration scanner and some resources"
			System.setProperty( WebAppRunner.WEBAPP_PRE_SCANNED_RESOURCE_NAME, '/web_fragmentXML')
			PreScannedConfiguration cfg = new PreScannedConfiguration()
			WebAppContext context = new WebAppContext()

		when: "we scan"
			cfg.preConfigure( context )

		then:
			context.metaData.webXml.getResource().getURI().toString() == cfg.applicationRoot + 'WEB-INF/web.xml'
			context.metaData.webInfJars.first().getURI().toString() == "${cfg.applicationRoot}jars/fragment.jar"
			context.metaData.fragments.first().name == 'fragmented'
	}


}