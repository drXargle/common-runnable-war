package nz.ac.auckland.war;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.CountDownLatch;

/**
 * Lists on localhost - this means integration tests can stop the container easily.
 */
public class PortWatcher implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(PortWatcher.class);

  private final CountDownLatch latch;
  private final ServerSocket listener;

  public PortWatcher(CountDownLatch latch, int port) {
    this.latch = latch;
    try {
      this.listener = new ServerSocket(port, 1, InetAddress.getByAddress(new byte[]{127, 0, 0, 1}));
    } catch (IOException e) {
      throw new RuntimeException("Cannot start port watcher", e);
    }
    log.debug("Port watcher is listening on port {} for shutdown", port);
  }

  @Override
  public void run() {
    try {
      try {
        listener.accept();
      } finally {
        listener.close();
      }
    } catch (IOException e) {

    }

    if (latch.getCount() > 0) {
      log.debug("Received tcp connection, shutting down");
      latch.countDown();
    }
  }
}
