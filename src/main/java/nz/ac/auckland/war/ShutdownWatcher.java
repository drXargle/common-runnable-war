package nz.ac.auckland.war;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class ShutdownWatcher implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ShutdownWatcher.class);

  private final CountDownLatch latch;

  public ShutdownWatcher(CountDownLatch latch) {
    this.latch = latch;
  }

  @Override
  public void run() {
    if (latch.getCount() > 0) {
      log.debug("Received signal, triggering shutdown");
      latch.countDown();
    }
  }
}
