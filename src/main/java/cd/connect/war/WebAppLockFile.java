package cd.connect.war;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;

/**
 * represents the lock file for the web app to ensure we don't run two copies with the samem config.
 *
 * If you want to run multiple copies on the same machine, make sure the lock file is different.
 */
public class WebAppLockFile {
  private final File file;
  private final FileChannel nioFile;

  public WebAppLockFile(String file) {
    this(new File(file));
  }

  public WebAppLockFile(File file) {
    try {
      this.file = file;

      this.nioFile = new RandomAccessFile(file, "rw").getChannel();

      if (nioFile.tryLock() == null)
        throw new IllegalStateException("Looks like we are already running, our lock file is already locked");
    } catch (IOException e) {
      throw new RuntimeException(String.format("Failed to lock file '%s'", file), e);
    }
  }

  public void release() {
    if (null == nioFile)
      throw new IllegalStateException("Lock file was already release");

    try {
      nioFile.close();
    } catch (IOException e) {
      throw new RuntimeException(String.format("Failed to close lock file '%s'", file), e);
    } finally {
      file.delete();
    }
  }

  @Override
  public String toString() {
    return file.toString();
  }
}
