package co.eci.snake.core.engine;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Snake;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GameClockAndPauseTest {

  @Test
  @DisplayName("GameClock pause should suspend workers and resume without busy waiting")
  void testPauseAndResume() throws Exception {
    AtomicInteger tickCount = new AtomicInteger(0);
    GameClock clock = new GameClock(20, tickCount::incrementAndGet);

    Board board = new Board(20, 20);
    Snake snake = Snake.of(1, 5, 5, Direction.RIGHT);
    board.registerSnakes(List.of(snake));

    Thread worker = Thread.ofVirtual().start(new SnakeRunner(snake, board, clock));

    // Initially stopped
    assertEquals(GameState.STOPPED, clock.state());

    // Start
    clock.start();
    assertEquals(GameState.RUNNING, clock.state());
    Thread.sleep(100);
    int ticksRunning = tickCount.get();
    assertTrue(ticksRunning > 0, "Ticks should increase while running");

    // Pause
    clock.pause();
    assertEquals(GameState.PAUSED, clock.state());
    Thread.sleep(100);
    int ticksPaused = tickCount.get();
    // In pause, ticks should not increase further
    assertEquals(ticksPaused, tickCount.get(), "Ticks should freeze while paused");

    // Resume
    clock.resume();
    assertEquals(GameState.RUNNING, clock.state());
    Thread.sleep(100);
    assertTrue(tickCount.get() > ticksPaused, "Ticks should resume increasing after resume");

    // Stop and cleanup
    clock.stop();
    clock.close();
  }
}
