package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.PauseController;
import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class SnakeApp extends JFrame {

  private enum UiState { STOPPED, RUNNING, PAUSED }

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JLabel statsLabel;
  private final GameClock clock;
  private final java.util.List<Snake> snakes = new java.util.ArrayList<>();
  private final PauseController pauseController = new PauseController();
  private final AtomicInteger deathSequence = new AtomicInteger(0);

  private ExecutorService snakesExecutor;
  private UiState uiState = UiState.STOPPED;

  public SnakeApp() {
    super("The Snake Race");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(i, x, y, dir));
    }

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");
    this.statsLabel = new JLabel("Presiona Iniciar para comenzar.");
    statsLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    var south = new JPanel(new BorderLayout());
    south.add(actionButton, BorderLayout.WEST);
    south.add(statsLabel, BorderLayout.CENTER);

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(south, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    actionButton.addActionListener((ActionEvent e) -> onActionButton());

    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "pause");
    gamePanel.getActionMap().put("pause", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        onActionButton();
      }
    });

    var player = snakes.get(0);
    InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    ActionMap am = gamePanel.getActionMap();
    im.put(KeyStroke.getKeyStroke("LEFT"), "left");
    im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
    im.put(KeyStroke.getKeyStroke("UP"), "up");
    im.put(KeyStroke.getKeyStroke("DOWN"), "down");
    am.put("left", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.LEFT);
      }
    });
    am.put("right", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.RIGHT);
      }
    });
    am.put("up", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.UP);
      }
    });
    am.put("down", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        player.turn(Direction.DOWN);
      }
    });

    if (snakes.size() > 1) {
      var p2 = snakes.get(1);
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.LEFT);
        }
      });
      am.put("p2-right", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.RIGHT);
        }
      });
      am.put("p2-up", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.UP);
        }
      });
      am.put("p2-down", new AbstractAction() {
        @Override
        public void actionPerformed(ActionEvent e) {
          p2.turn(Direction.DOWN);
        }
      });
    }

    setVisible(true);
  }

  /**
   * Botón único Iniciar -> Pausar -> Reanudar -> Pausar ...
   * (cumple el punto 3: Iniciar / Pausar / Reanudar).
   */
  private void onActionButton() {
    switch (uiState) {
      case STOPPED -> startGame();
      case RUNNING -> pauseGame();
      case PAUSED -> resumeGame();
    }
  }

  private void startGame() {
    snakesExecutor = Executors.newVirtualThreadPerTaskExecutor();
    snakes.forEach(s -> snakesExecutor.submit(new SnakeRunner(s, board, pauseController, deathSequence)));
    clock.start();
    uiState = UiState.RUNNING;
    actionButton.setText("Pausar");
    statsLabel.setText("Carrera en curso...");
  }

  private void pauseGame() {
    clock.pause();

    long aliveCount = snakes.stream().filter(Snake::isAlive).count();
    pauseController.pause((int) aliveCount);
    try {
      // Timeout defensivo: si por alguna razon algun hilo no llega a
      // confirmar (p. ej. ya termino porque murio justo en ese instante),
      // no queremos bloquear la UI para siempre.
      pauseController.awaitQuiescence(500);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    }

    // A partir de aqui ningun SnakeRunner esta mutando estado (todos los
    // que seguian vivos quedaron bloqueados en el monitor de
    // PauseController), asi que leer longitudes/estado de vida es seguro:
    // no hay tearing entre "cuenta las serpientes vivas" y "lee su longitud".
    statsLabel.setText(buildStatsText());

    uiState = UiState.PAUSED;
    actionButton.setText("Reanudar");
  }

  private void resumeGame() {
    pauseController.resume();
    clock.resume();
    uiState = UiState.RUNNING;
    actionButton.setText("Pausar");
    statsLabel.setText("Carrera en curso...");
  }

  private String buildStatsText() {
    Snake longestAlive = null;
    Snake firstDead = null;

    for (Snake s : snakes) {
      if (s.isAlive()) {
        if (longestAlive == null || s.length() > longestAlive.length()) {
          longestAlive = s;
        }
      } else {
        if (firstDead == null || s.deathOrder() < firstDead.deathOrder()) {
          firstDead = s;
        }
      }
    }

    String best = (longestAlive == null)
        ? "ninguna serpiente viva"
        : "S" + longestAlive.id() + " (longitud " + longestAlive.length() + ")";
    String worst = (firstDead == null)
        ? "ninguna serpiente ha muerto aun"
        : "S" + firstDead.id() + " (murio primero, orden " + firstDead.deathOrder() + ")";

    return "<html>Mejor: " + best + " &nbsp;&nbsp;|&nbsp;&nbsp; Peor: " + worst + "</html>";
  }

  public static final class GamePanel extends JPanel {
    private final Board board;
    private final Supplier snakesSupplier;
    private final int cell = 20;

    @FunctionalInterface
    public interface Supplier {
      List<Snake> get();
    }

    public GamePanel(Board board, Supplier snakesSupplier) {
      this.board = board;
      this.snakesSupplier = snakesSupplier;
      setPreferredSize(new Dimension(board.width() * cell + 1, board.height() * cell + 40));
      setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      var g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

      g2.setColor(new Color(220, 220, 220));
      for (int x = 0; x <= board.width(); x++)
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      for (int y = 0; y <= board.height(); y++)
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);

      // Obstáculos
      g2.setColor(new Color(255, 102, 0));
      for (var p : board.obstacles()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillRect(x + 2, y + 2, cell - 4, cell - 4);
        g2.setColor(Color.RED);
        g2.drawLine(x + 4, y + 4, x + cell - 6, y + 4);
        g2.drawLine(x + 4, y + 8, x + cell - 6, y + 8);
        g2.drawLine(x + 4, y + 12, x + cell - 6, y + 12);
        g2.setColor(new Color(255, 102, 0));
      }

      // Ratones
      g2.setColor(Color.BLACK);
      for (var p : board.mice()) {
        int x = p.x() * cell, y = p.y() * cell;
        g2.fillOval(x + 4, y + 4, cell - 8, cell - 8);
        g2.setColor(Color.WHITE);
        g2.fillOval(x + 8, y + 8, cell - 16, cell - 16);
        g2.setColor(Color.BLACK);
      }

      // Teleports (flechas rojas)
      Map<Position, Position> tp = board.teleports();
      g2.setColor(Color.RED);
      for (var entry : tp.entrySet()) {
        Position from = entry.getKey();
        int x = from.x() * cell, y = from.y() * cell;
        int[] xs = { x + 4, x + cell - 4, x + cell - 10, x + cell - 10, x + 4 };
        int[] ys = { y + cell / 2, y + cell / 2, y + 4, y + cell - 4, y + cell / 2 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Turbo (rayos)
      g2.setColor(Color.BLACK);
      for (var p : board.turbo()) {
        int x = p.x() * cell, y = p.y() * cell;
        int[] xs = { x + 8, x + 12, x + 10, x + 14, x + 6, x + 10 };
        int[] ys = { y + 2, y + 2, y + 8, y + 8, y + 16, y + 10 };
        g2.fillPolygon(xs, ys, xs.length);
      }

      // Serpientes
      var snakes = snakesSupplier.get();
      int idx = 0;
      for (Snake s : snakes) {
        var body = s.snapshot().toArray(new Position[0]);
        boolean alive = s.isAlive();
        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          Color base = (idx == 0) ? new Color(0, 170, 0) : new Color(0, 160, 180);
          if (!alive) base = new Color(140, 140, 140); // serpientes muertas se pintan grises
          int shade = Math.max(0, 40 - i * 4);
          g2.setColor(new Color(
              Math.min(255, base.getRed() + shade),
              Math.min(255, base.getGreen() + shade),
              Math.min(255, base.getBlue() + shade)));
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
        idx++;
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
