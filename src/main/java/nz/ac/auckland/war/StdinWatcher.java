package nz.ac.auckland.war;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;

public class StdinWatcher implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(StdinWatcher.class);

  private final CountDownLatch latch;
  private final InputStream stream;

  public StdinWatcher(CountDownLatch latch) {
    this.latch = latch;
    this.stream = System.in;

    log.debug("Watching stdin");
  }

  @Override
  public void run() {
    try {
      while (stream.read() >= 0) /* wait */ ;
    } catch (IOException e) {
        /* ignored */
    }
    if (latch.getCount() > 0) {
      log.debug("shutting down as no longer able to read stdin");
      latch.countDown();
    }
  }
}
