package nz.ac.auckland.war;

import com.bluetrainsoftware.classpathscanner.ResourceScanListener;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * author: Irina Benediktovich - http://plus.google.com/+IrinaBenediktovich
 */
public class ScanConfigurationTest {
	protected static final Path PATH_TARGET_RESOURCES = ScanConfiguration.PATH_TARGET_RESOURCES;
	protected static final Path PATH_TARGET_TEST_RESOURCES = ScanConfiguration.PATH_TARGET_TEST_RESOURCES;

	private static final Path PATH_SRC_RESOURCES = Paths.get("src/main/resources/META-INF/resources");
	private static final Path PATH_SRC_TEST_RESOURCES = Paths.get("src/test/resources/META-INF/resources");
	private static final int TARGET_RES_LEN = ScanConfiguration.PATH_TARGET_RESOURCES.getNameCount();

	/**
	 * Make sure we understand how nio is working on given OS
	 * We want to replace
	 * ..\common-runnable-war/target/classes/META-INF/resources
	 * with
	 * ..\common-runnable-war/src/main/resources/META-INF/resources
	 * @throws Exception
	 */
	public void testPathTraverse() throws Exception{
		// test endsWith()
		Path winFile = Paths.get("C:\\dev\\uoagit\\common-runnable-war\\target\\classes\\META-INF\\resources\\");
		Path unixFile = Paths.get("/home/user/dev/common-runnable-war/target/classes/META-INF/resources/");

		assertThat(winFile.endsWith(ScanConfiguration.PATH_TARGET_RESOURCES)).isTrue();

		assert winFile.endsWith(ScanConfiguration.PATH_TARGET_RESOURCES);
		assert unixFile.endsWith(ScanConfiguration.PATH_TARGET_RESOURCES);

		// intention is to replace target/classes with src/main/resources
		Path result = winFile.getRoot().resolve(winFile.subpath(0, winFile.getNameCount()-TARGET_RES_LEN)).resolve(PATH_SRC_RESOURCES);
		assert result.endsWith("common-runnable-war/src/main/resources/META-INF/resources");
		result = unixFile.getRoot().resolve(unixFile.subpath(0, unixFile.getNameCount()-TARGET_RES_LEN)).resolve(PATH_SRC_RESOURCES);
		assert result.endsWith("common-runnable-war/src/main/resources/META-INF/resources");


		// test File.toPath()
		File file = new File (this.getClass().getResource("/nz/ac/auckland/war/webdefault.xml").toURI());
		assert file.exists();
		String p = file.getAbsolutePath(); // ..\common-runnable-war\target\classes\nz\ac\auckland\war\webdefault.xml
		assert p.replaceAll("\\\\", "/").contains("target/classes");

		Path absoluteFilePath = file.toPath();
		Path packageFilePath = Paths.get("nz/ac/auckland/war/webdefault.xml");

		assert absoluteFilePath.endsWith(packageFilePath);
	}

	@Test
	public void testMorphDevelopmentResource() throws Exception{
		ScanConfiguration scan = new ScanConfiguration();

		File file = new File (this.getClass().getResource("/META-INF/resources").toURI());
		assert file.exists();

		ResourceScanListener.ScanResource res = new ResourceScanListener.ScanResource(null, file, null);
		List<URL> result = scan.morphDevelopmentResource(res);
		assert result.get(0).toString().replaceAll("\\\\", "/").contains("src/test/resources/META-INF/resources");
	}

}