package co.eci.snake.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Snake {
  // Region critica: todo acceso/mutacion del cuerpo pasa por bodyLock.
  // ArrayDeque no es thread-safe y "body" es leido (snapshot para pintar,
  // length() para las estadisticas al pausar) desde hilos distintos al que
  // lo muta (SnakeRunner via advance()). Antes solo Board.step() era
  // synchronized, pero eso no protegia la lectura concurrente de "body"
  // hecha por la UI (repaint) ni por el calculo de estadisticas al pausar.
  private final Object bodyLock = new Object();
  private final Deque<Position> body = new ArrayDeque<>();
  private int maxLength = 5;

  private volatile Direction direction;

  // Estado de "vida" para el punto 3 (mejor/peor serpiente al pausar).
  // Se usa AtomicBoolean + compareAndSet para que, si dos hilos llegaran a
  // reportar la muerte de la misma serpiente (no debería pasar con el diseño
  // actual, pero es defensivo), solo uno gane y se registre un único orden.
  private final AtomicBoolean alive = new AtomicBoolean(true);
  private volatile int deathOrder = -1; // -1 => viva o aun no se registro

  private final int id;
  private int lives = 3; // ver justificacion en el reporte (punto 3)

  private Snake(int id, Position start, Direction dir) {
    this.id = id;
    body.addFirst(start);
    this.direction = dir;
  }

  public static Snake of(int id, int x, int y, Direction dir) {
    return new Snake(id, new Position(x, y), dir);
  }

  public int id() { return id; }

  public Direction direction() { return direction; }

  public void turn(Direction dir) {
    // "direction" es volatile: turn() puede ser llamado desde el hilo de UI
    // (KeyListener) y desde SnakeRunner (randomTurn). Es una carrera benigna
    // (last-write-wins) sobre una referencia inmutable de 4 valores; no
    // corrompe estado y no necesita lock (documentado en el reporte).
    if ((direction == Direction.UP && dir == Direction.DOWN) ||
        (direction == Direction.DOWN && dir == Direction.UP) ||
        (direction == Direction.LEFT && dir == Direction.RIGHT) ||
        (direction == Direction.RIGHT && dir == Direction.LEFT)) {
      return;
    }
    this.direction = dir;
  }

  public Position head() {
    synchronized (bodyLock) {
      return body.peekFirst();
    }
  }

  public Deque<Position> snapshot() {
    synchronized (bodyLock) {
      return new ArrayDeque<>(body);
    }
  }

  public int length() {
    synchronized (bodyLock) {
      return body.size();
    }
  }

  public void advance(Position newHead, boolean grow) {
    synchronized (bodyLock) {
      body.addFirst(newHead);
      if (grow) maxLength++;
      while (body.size() > maxLength) body.removeLast();
    }
  }

  public boolean isAlive() { return alive.get(); }

  public int deathOrder() { return deathOrder; }

  /** Resta una vida al chocar con un obstaculo. Devuelve true si con este golpe murio. */
  public boolean loseLifeAndCheckDeath(int order) {
    if (!alive.get()) return false;
    lives--;
    if (lives <= 0 && alive.compareAndSet(true, false)) {
      deathOrder = order;
      return true;
    }
    return false;
  }

  public int lives() { return lives; }
}
