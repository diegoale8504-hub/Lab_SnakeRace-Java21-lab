package co.eci.snake.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public final class Board {
  private final int width;
  private final int height;

  private final Set<Position> mice = ConcurrentHashMap.newKeySet();
  private final Set<Position> obstacles = ConcurrentHashMap.newKeySet();
  private final Set<Position> turbo = ConcurrentHashMap.newKeySet();
  private final Map<Position, Position> teleports = new ConcurrentHashMap<>();


  private final List<Snake> snakes = new CopyOnWriteArrayList<>();
  private final List<Snake> deathOrder = new CopyOnWriteArrayList<>();

  public enum MoveResult { MOVED, ATE_MOUSE, HIT_OBSTACLE, ATE_TURBO, TELEPORTED, DIED }

  public Board(int width, int height) {
    if (width <= 0 || height <= 0) throw new IllegalArgumentException("Board dimensions must be positive");
    this.width = width;
    this.height = height;
    for (int i = 0; i < 6; i++) mice.add(randomEmpty());
    for (int i = 0; i < 4; i++) obstacles.add(randomEmpty());
    for (int i = 0; i < 3; i++) turbo.add(randomEmpty());
    createTeleportPairs(2);
  }

  public void registerSnakes(List<Snake> snakesList) {
    snakes.clear();
    if (snakesList != null) {
      snakes.addAll(snakesList);
    }
  }

  public int width() { return width; }
  public int height() { return height; }

  public Set<Position> mice() { return new HashSet<>(mice); }
  public Set<Position> obstacles() { return new HashSet<>(obstacles); }
  public Set<Position> turbo() { return new HashSet<>(turbo); }
  public Map<Position, Position> teleports() { return Collections.unmodifiableMap(teleports); }
  public List<Snake> deathOrder() { return Collections.unmodifiableList(deathOrder); }

  public synchronized void recordDeath(Snake snake) {
    if (snake != null && !deathOrder.contains(snake)) {
      deathOrder.add(snake);
    }
  }
  public MoveResult step(Snake snake) {
    Objects.requireNonNull(snake, "snake");
    if (!snake.isAlive()) return MoveResult.DIED;

    var head = snake.head();
    if (head == null) return MoveResult.DIED;
    var dir = snake.direction();
    Position next = new Position(head.x() + dir.dx, head.y() + dir.dy).wrap(width, height);

    if (obstacles.contains(next)) return MoveResult.HIT_OBSTACLE;

    boolean teleported = false;
    Position dest = teleports.get(next);
    if (dest != null) {
      next = dest;
      teleported = true;
    }

    // Colisión con otras serpientes o contra sí misma
    for (Snake other : snakes) {
      if (other.isAlive() && other.contains(next)) {
        snake.die();
        recordDeath(snake);
        return MoveResult.DIED;
      }
    }

    boolean ateMouse = mice.remove(next);
    boolean ateTurbo = turbo.remove(next);

    snake.advance(next, ateMouse);

    if (ateMouse) {
      Position newMouse = randomEmpty();
      if (newMouse != null) mice.add(newMouse);
      Position newObs = randomEmpty();
      if (newObs != null) obstacles.add(newObs);
      if (ThreadLocalRandom.current().nextDouble() < 0.2) {
        Position newTurbo = randomEmpty();
        if (newTurbo != null) turbo.add(newTurbo);
      }
    }

    if (ateTurbo) return MoveResult.ATE_TURBO;
    if (ateMouse) return MoveResult.ATE_MOUSE;
    if (teleported) return MoveResult.TELEPORTED;
    return MoveResult.MOVED;
  }

  private void createTeleportPairs(int pairs) {
    for (int i = 0; i < pairs; i++) {
      Position a = randomEmpty();
      Position b = randomEmpty();
      if (a != null && b != null) {
        teleports.put(a, b);
        teleports.put(b, a);
      }
    }
  }

  private boolean isOccupiedBySnake(Position p) {
    for (Snake s : snakes) {
      if (s.contains(p)) return true;
    }
    return false;
  }

  private Position randomEmpty() {
    var rnd = ThreadLocalRandom.current();
    Position p;
    int guard = 0;
    do {
      p = new Position(rnd.nextInt(width), rnd.nextInt(height));
      guard++;
      if (guard > width * height * 2) break;
    } while (mice.contains(p) || obstacles.contains(p) || turbo.contains(p) || teleports.containsKey(p) || isOccupiedBySnake(p));
    return p;
  }
}
