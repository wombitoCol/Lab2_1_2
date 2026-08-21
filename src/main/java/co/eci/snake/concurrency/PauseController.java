package co.eci.snake.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class PauseController {
  private final Object monitor = new Object();
  private volatile boolean paused = false;
  private volatile CountDownLatch quiescenceLatch = new CountDownLatch(0);

  public void pause(int expectedRunningThreads) {
    synchronized (monitor) {
      quiescenceLatch = new CountDownLatch(Math.max(expectedRunningThreads, 0));
      paused = true;
    }
  }

  public boolean awaitQuiescence(long timeoutMs) throws InterruptedException {
    return quiescenceLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
  }

  public void resume() {
    synchronized (monitor) {
      paused = false;
      monitor.notifyAll();
    }
  }

  public boolean isPaused() { return paused; }


  public void checkPausePoint() throws InterruptedException {
    if (!paused) return;
    synchronized (monitor) {
      if (!paused) return; // re-chequeo dentro del monitor (evita condicion de carrera)
      quiescenceLatch.countDown(); // cuenta UNA sola vez esta "visita" a pausa
      while (paused) {
        monitor.wait();
      }
    }
  }
}
