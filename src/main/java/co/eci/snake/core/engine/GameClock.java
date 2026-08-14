package co.eci.snake.core.engine;

import co.eci.snake.core.GameState;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class GameClock implements AutoCloseable {
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
  private final long periodMillis;
  private final Runnable tick;
  private final AtomicReference<GameState> state = new AtomicReference<>(GameState.STOPPED);
  private final Object pauseLock = new Object();

  public GameClock(long periodMillis, Runnable tick) {
    if (periodMillis <= 0) throw new IllegalArgumentException("periodMillis must be > 0");
    this.periodMillis = periodMillis;
    this.tick = Objects.requireNonNull(tick, "tick");
  }

  public GameState state() {
    return state.get();
  }

  public boolean isRunning() {
    return state.get() == GameState.RUNNING;
  }

  public boolean isPaused() {
    return state.get() == GameState.PAUSED;
  }

  public void start() {
    if (state.compareAndSet(GameState.STOPPED, GameState.RUNNING)) {
      scheduler.scheduleAtFixedRate(() -> {
        if (state.get() == GameState.RUNNING) {
          tick.run();
        }
      }, 0, periodMillis, TimeUnit.MILLISECONDS);
      synchronized (pauseLock) {
        pauseLock.notifyAll();
      }
    }
  }

  public void pause() {
    state.set(GameState.PAUSED);
  }

  public void resume() {
    if (state.compareAndSet(GameState.PAUSED, GameState.RUNNING)) {
      synchronized (pauseLock) {
        pauseLock.notifyAll();
      }
    }
  }

  public void stop() {
    state.set(GameState.STOPPED);
    synchronized (pauseLock) {
      pauseLock.notifyAll();
    }
  }

  /**
   * Punto de sincronización para que los hilos trabajadores se suspendan
   * pasivamente (wait) sin consumir ciclos de CPU mientras el juego esté pausado o detenido.
   */
  public void awaitIfPaused() throws InterruptedException {
    synchronized (pauseLock) {
      while (state.get() == GameState.PAUSED) {
        pauseLock.wait();
      }
    }
  }

  @Override
  public void close() {
    stop();
    scheduler.shutdownNow();
  }
}
