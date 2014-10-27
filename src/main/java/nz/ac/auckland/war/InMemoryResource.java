package nz.ac.auckland.war;

import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class InMemoryResource extends Resource {
	private static final Logger log =  LoggerFactory.getLogger(InMemoryResource.class);

	class ByteArray {
		byte bytes[];
	}

	private Map<String,InMemoryResource> resources;
	private final ByteArray self;
	private final InMemoryResource parent;
	private final String fileName;
	private String[] files = null;

	public InMemoryResource() {
		parent = null;
		self = null;
		fileName = null;
	}

	public InMemoryResource(InMemoryResource parent, String path) {
		self = null;
		fileName = path;
		this.parent = parent;
	}

	public InMemoryResource(InMemoryResource parent, InputStream stream, String fileName) {
		this.parent = parent;

		try {
			self = new ByteArray();
			if (stream != null) {
				self.bytes = IO.readBytes(stream);
			}
		} catch (IOException e) {
			throw new RuntimeException(String.format("Unable to read input stream for %s", fileName) );
		}

		this.fileName = fileName;
	}

	protected void addResource(InMemoryResource child, String path) {
		if (resources == null) {
			resources = new HashMap<>();
		}

		resources.put(path, child);

		// cache the file list
		String [] nFiles = new String[resources.size()];

		int count = 0;

		for(Map.Entry<String, InMemoryResource> key : resources.entrySet()) {
			if (key.getValue().isDirectory()) {
				nFiles[count++] = key.getKey() + "/";
			} else {
				nFiles[count++] = key.getKey();
			}
		}

		files = nFiles;
	}

	public InMemoryResource addDirectory(String path) {
		InMemoryResource child = findPath(path);

		if (child == null) {
			child = new InMemoryResource(this, path);
			addResource(child, path);
		}

		return child;
	}

	public InMemoryResource addFile(String path, InputStream stream) {
		InMemoryResource child = findPath(path);
		if (child == null) {
			child = new InMemoryResource(this, stream, path);
			addResource(child, path);
		}
		return child;
	}

	InMemoryResource getParent() {
		return parent;
	}

	@Override
	public boolean isContainedIn(Resource r) throws MalformedURLException {
		return (r instanceof InMemoryResource) && ((InMemoryResource)r).getParent() == this;
	}

	@Override
	public void close() {
		// ignore
	}

	@Override
	public boolean exists() {
		return self == null || self.bytes != null;
	}

	@Override
	public boolean isDirectory() {
		return self == null;
	}

	@Override
	public long lastModified() {
		return 0;
	}

	@Override
	public long length() {
		return (self == null || self.bytes == null) ? 0 : self.bytes.length;
	}

	@Override
	public URL getURL() {
		return null;
	}

	@Override
	public File getFile() throws IOException {
		return null;
	}

	@Override
	public String getName() {
		return fileName;
	}

	@Override
	public InputStream getInputStream() throws IOException {
		if (self == null || self.bytes == null) {
			return null;
		} else {
			return new ByteArrayInputStream(self.bytes);
		}
	}

	@Override
	public ReadableByteChannel getReadableByteChannel() throws IOException {
		return null;
	}

	@Override
	public boolean delete() throws SecurityException {
		return false;
	}

	@Override
	public boolean renameTo(Resource dest) throws SecurityException {
		return false;
	}

	@Override
	public String[] list() {
		return files;
	}

	public InMemoryResource findPath(String path) {
		if (resources != null) {
			return resources.get(path);
		} else {
			return null;
		}
	}

	@Override
	public Resource addPath(String path) throws IOException {
		if ("/".equals(path)) {
//			log.info("INMEMORYREQUEST: {} - top level", path);
			return this;
		}

		InMemoryResource resource = this;

		String[] fullPath = path.split("/");
		for(String partPath: fullPath) {
			if (partPath.length() == 0) continue; // empty /css/ becomes "", "css"

			resource = resource.findPath(partPath);

			if (resource == null) {
//				log.info("INMEMORYREQUEST: {} - could not find", path);

				// this will garbage collect, don't hang onto it
				return new InMemoryResource(this, null, path);
			}
		}

//		log.info("INMEMORYREQUEST: {} - found", path);

		return resource;
	}

	@Override
	public String toString() {
		return fileName;
	}
}
