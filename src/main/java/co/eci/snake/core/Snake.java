package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class Snake {
  private final int id;
  private final Deque<Position> body = new ArrayDeque<>();
  private volatile Direction direction;
  private int maxLength = 5;
  private volatile boolean alive = true;

  private Snake(int id, Position start, Direction dir) {
    this.id = id;
    this.body.addFirst(Objects.requireNonNull(start, "start"));
    this.direction = Objects.requireNonNull(dir, "dir");
  }

  public static Snake of(int id, int x, int y, Direction dir) {
    return new Snake(id, new Position(x, y), dir);
  }

  public static Snake of(int x, int y, Direction dir) {
    return new Snake(0, new Position(x, y), dir);
  }

  public int id() {
    return id;
  }

  public boolean isAlive() {
    return alive;
  }

  public synchronized void die() {
    this.alive = false;
  }

  public Direction direction() {
    return direction;
  }

  public synchronized void turn(Direction dir) {
    if (dir == null) return;
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

<<<<<<< HEAD
  public synchronized Position head() { return body.peekFirst(); }

  public synchronized Deque<Position> snapshot() { return new ArrayDeque<>(body); }

  public synchronized void advance(Position newHead, boolean grow) {
=======
  public synchronized Position head() {
    return body.peekFirst();
  }

  public synchronized Deque<Position> snapshot() {
    return new ArrayDeque<>(body);
  }

  public synchronized int length() {
    return body.size();
  }

  public synchronized boolean contains(Position p) {
    if (p == null) return false;
    return body.contains(p);
  }

  public synchronized void advance(Position newHead, boolean grow) {
    if (!alive) return;
>>>>>>> Diego
    body.addFirst(newHead);
    if (grow) maxLength++;
    while (body.size() > maxLength) {
      body.removeLast();
    }
  }

  @Override
  public String toString() {
    return "Snake #" + id + " (longitud=" + length() + ", " + (alive ? "VIVA" : "MUERTA") + ")";
  }
}
