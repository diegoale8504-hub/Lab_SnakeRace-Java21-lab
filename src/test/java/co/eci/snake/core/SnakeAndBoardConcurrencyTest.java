package co.eci.snake.core;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.engine.GameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class SnakeAndBoardConcurrencyTest {

  @Test
  @DisplayName("Snake body operations should grow up to maxLength and extend with grow=true")
  void testSnakeGrowthAndThreadSafety() {
    Snake snake = Snake.of(1, 0, 0, Direction.RIGHT);
    assertEquals(1, snake.length());
    assertTrue(snake.isAlive());

    // Advance 4 times without growing maxLength (maxLength starts at 5)
    snake.advance(new Position(1, 0), false);
    snake.advance(new Position(2, 0), false);
    snake.advance(new Position(3, 0), false);
    snake.advance(new Position(4, 0), false);
    assertEquals(5, snake.length());
    assertEquals(new Position(4, 0), snake.head());

    // 6th advance without grow: tail is trimmed to maintain maxLength = 5
    snake.advance(new Position(5, 0), false);
    assertEquals(5, snake.length());
    assertEquals(new Position(5, 0), snake.head());
    assertFalse(snake.contains(new Position(0, 0)));

    // Advance with grow: maxLength becomes 6
    snake.advance(new Position(6, 0), true);
    assertEquals(6, snake.length());
    assertTrue(snake.contains(new Position(1, 0)));
  }

  @Test
  @DisplayName("Collision with another snake's body should cause death and record death order")
  void testSnakeCollisionAndDeathOrder() {
    Board board = new Board(50, 50);

    Position p1 = new Position(10, 10);
    Position p2 = new Position(11, 10);
    while (board.obstacles().contains(p2) || board.teleports().containsKey(p2)) {
      p1 = new Position(p1.x() + 2, p1.y());
      p2 = new Position(p1.x() + 1, p1.y());
    }

    Snake s1 = Snake.of(1, p1.x(), p1.y(), Direction.RIGHT);
    Snake s2 = Snake.of(2, p2.x(), p2.y(), Direction.LEFT);

    board.registerSnakes(List.of(s1, s2));

    // s1 moving right into p2 which is occupied by s2
    Board.MoveResult res = board.step(s1);
    assertEquals(Board.MoveResult.DIED, res);
    assertFalse(s1.isAlive());

    // Death order should contain s1 as the first dead snake
    List<Snake> deaths = board.deathOrder();
    assertEquals(1, deaths.size());
    assertEquals(s1, deaths.get(0));
  }

  @Test
  @DisplayName("High load test with N=30 virtual threads, pausing and checking stats without exceptions")
  void testRobustnessUnderHighLoad() throws InterruptedException {
    int N = 30;
    Board board = new Board(40, 40);
    List<Snake> snakes = new ArrayList<>();
    for (int i = 0; i < N; i++) {
      int x = (2 + i * 2) % board.width();
      int y = (2 + i * 2) % board.height();
      snakes.add(Snake.of(i + 1, x, y, Direction.values()[i % Direction.values().length]));
    }
    board.registerSnakes(snakes);

    GameClock clock = new GameClock(20, () -> {});
    ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, clock)));

    clock.start();
    Thread.sleep(300);

    // Pause under high load
    clock.pause();
    Thread.sleep(100);

    // Consistency check on paused state: snapshot and stats calculation without tearing
    var longestAlive = snakes.stream().filter(Snake::isAlive).max(Comparator.comparingInt(Snake::length));
    var deadList = board.deathOrder();

    assertDoesNotThrow(() -> {
      for (Snake s : snakes) {
        var snap = s.snapshot();
        assertNotNull(snap);
      }
    });

    assertTrue(longestAlive.isPresent() || deadList.size() == N);

    // Resume under high load
    clock.resume();
    Thread.sleep(200);

    clock.stop();
    clock.close();
    exec.shutdownNow();
  }
}
