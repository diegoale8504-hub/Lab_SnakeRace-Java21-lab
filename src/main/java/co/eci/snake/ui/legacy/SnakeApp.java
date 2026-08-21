package co.eci.snake.ui.legacy;

import co.eci.snake.concurrency.SnakeRunner;
import co.eci.snake.core.Board;
import co.eci.snake.core.Direction;
import co.eci.snake.core.GameState;
import co.eci.snake.core.Position;
import co.eci.snake.core.Snake;
import co.eci.snake.core.engine.GameClock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public final class SnakeApp extends JFrame {

  private final Board board;
  private final GamePanel gamePanel;
  private final JButton actionButton;
  private final JLabel statusLabel;
  private final GameClock clock;
<<<<<<< HEAD
  private final java.util.List<Snake> snakes = new java.util.concurrent.CopyOnWriteArrayList<>();
=======
  private final List<Snake> snakes = new CopyOnWriteArrayList<>();
  private boolean started = false;
>>>>>>> Diego

  public SnakeApp() {
    super("The Snake Race — Concurrencia Segura");
    this.board = new Board(35, 28);

    int N = Integer.getInteger("snakes", 2);
    for (int i = 0; i < N; i++) {
      int x = 2 + (i * 3) % board.width();
      int y = 2 + (i * 2) % board.height();
      var dir = Direction.values()[i % Direction.values().length];
      snakes.add(Snake.of(i + 1, x, y, dir));
    }
    board.registerSnakes(snakes);

    this.gamePanel = new GamePanel(board, () -> snakes);
    this.actionButton = new JButton("Iniciar");
    this.actionButton.setFont(new Font("SansSerif", Font.BOLD, 14));
    this.actionButton.setFocusable(false);

    this.statusLabel = new JLabel("Estado: Listo para iniciar | Serpientes: " + N, SwingConstants.CENTER);
    this.statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
    this.statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

    JPanel bottomPanel = new JPanel(new BorderLayout(8, 0));
    bottomPanel.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
    bottomPanel.add(statusLabel, BorderLayout.CENTER);
    bottomPanel.add(actionButton, BorderLayout.EAST);

    setLayout(new BorderLayout());
    add(gamePanel, BorderLayout.CENTER);
    add(bottomPanel, BorderLayout.SOUTH);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    pack();
    setLocationRelativeTo(null);

    this.clock = new GameClock(60, () -> SwingUtilities.invokeLater(gamePanel::repaint));

    actionButton.addActionListener((ActionEvent e) -> handleAction());

    // Control de pausa con la barra espaciadora
    gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("SPACE"), "action-toggle");
    gamePanel.getActionMap().put("action-toggle", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        handleAction();
      }
    });

    // Controles de Jugador 1 (Flechas)
    if (!snakes.isEmpty()) {
      var player1 = snakes.get(0);
      InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap am = gamePanel.getActionMap();
      im.put(KeyStroke.getKeyStroke("LEFT"), "left");
      im.put(KeyStroke.getKeyStroke("RIGHT"), "right");
      im.put(KeyStroke.getKeyStroke("UP"), "up");
      im.put(KeyStroke.getKeyStroke("DOWN"), "down");
      am.put("left", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player1.turn(Direction.LEFT); }
      });
      am.put("right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player1.turn(Direction.RIGHT); }
      });
      am.put("up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player1.turn(Direction.UP); }
      });
      am.put("down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player1.turn(Direction.DOWN); }
      });
    }

    // Controles de Jugador 2 (WASD)
    if (snakes.size() > 1) {
      var player2 = snakes.get(1);
      InputMap im = gamePanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
      ActionMap am = gamePanel.getActionMap();
      im.put(KeyStroke.getKeyStroke('A'), "p2-left");
      im.put(KeyStroke.getKeyStroke('D'), "p2-right");
      im.put(KeyStroke.getKeyStroke('W'), "p2-up");
      im.put(KeyStroke.getKeyStroke('S'), "p2-down");
      am.put("p2-left", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player2.turn(Direction.LEFT); }
      });
      am.put("p2-right", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player2.turn(Direction.RIGHT); }
      });
      am.put("p2-up", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player2.turn(Direction.UP); }
      });
      am.put("p2-down", new AbstractAction() {
        @Override public void actionPerformed(ActionEvent e) { player2.turn(Direction.DOWN); }
      });
    }

    setVisible(true);
  }

  private synchronized void handleAction() {
    if (!started) {
      // INICIAR
      started = true;
      actionButton.setText("Pausar");
      statusLabel.setText("Estado: 🟢 En Carrera");
      var exec = Executors.newVirtualThreadPerTaskExecutor();
      snakes.forEach(s -> exec.submit(new SnakeRunner(s, board, clock)));
      clock.start();
    } else if (clock.isRunning()) {
      // PAUSAR
      clock.pause();
<<<<<<< HEAD
      board.pause();
    } else {
      actionButton.setText("Action");
      clock.resume();
      board.resume();
=======
      actionButton.setText("Reanudar");
      statusLabel.setText("Estado: ⏸️ En Pausa");
      showPauseStatistics();
    } else if (clock.isPaused()) {
      // REANUDAR
      clock.resume();
      actionButton.setText("Pausar");
      statusLabel.setText("Estado: 🟢 En Carrera");
>>>>>>> Diego
    }
  }

  private void showPauseStatistics() {
    // Coordinación y recolección segura de estadísticas sin data tearing
    var longestAlive = snakes.stream()
        .filter(Snake::isAlive)
        .max(Comparator.comparingInt(Snake::length));

    var deadSnakes = board.deathOrder();
    long totalVivas = snakes.stream().filter(Snake::isAlive).count();
    long totalMuertas = deadSnakes.size();

    StringBuilder sb = new StringBuilder();
    sb.append("<html><body style='font-family: sans-serif; padding: 6px;'>");
    sb.append("<h2 style='color: #2c3e50; margin-bottom: 8px;'>⏸️ Estadísticas del Juego en Pausa</h2>");
    sb.append("<hr style='border: 1px solid #bdc3c7;'/>");

    sb.append("<p style='margin-top: 10px;'><b>🏆 Serpiente viva más larga:</b><br/>");
    if (longestAlive.isPresent()) {
      Snake s = longestAlive.get();
      sb.append("&nbsp;&nbsp;• <b>Serpiente #").append(s.id()).append("</b> con longitud de <b>")
        .append(s.length()).append(" casillas</b>.<br/>");
      Position head = s.head();
      if (head != null) {
        sb.append("&nbsp;&nbsp;• Posición actual de la cabeza: (").append(head.x()).append(", ").append(head.y()).append(")<br/>");
      }
    } else {
      sb.append("&nbsp;&nbsp;• <i>¡Todas las serpientes han muerto!</i><br/>");
    }
    sb.append("</p>");

    sb.append("<p style='margin-top: 12px;'><b>💀 Peor serpiente (primera en morir):</b><br/>");
    if (!deadSnakes.isEmpty()) {
      Snake firstDead = deadSnakes.get(0);
      sb.append("&nbsp;&nbsp;• <b>Serpiente #").append(firstDead.id())
        .append("</b> fue la primera en fallecer (Longitud final: ").append(firstDead.length()).append(").<br/>");
    } else {
      sb.append("&nbsp;&nbsp;• <i>¡Ninguna serpiente ha muerto todavía!</i><br/>");
    }
    sb.append("</p>");

    sb.append("<hr style='border: 1px solid #ecf0f1;'/>");
    sb.append("<p style='font-size: 11px; color: #7f8c8d;'>");
    sb.append("Serpientes vivas: <b>").append(totalVivas).append("</b> | Serpientes eliminadas: <b>").append(totalMuertas).append("</b>");
    sb.append("</p>");
    sb.append("</body></html>");

    JOptionPane.showMessageDialog(
        this,
        new JLabel(sb.toString()),
        "Pausa — Estadísticas Consistentes",
        JOptionPane.INFORMATION_MESSAGE
    );
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

      g2.setColor(new Color(225, 225, 225));
      for (int x = 0; x <= board.width(); x++) {
        g2.drawLine(x * cell, 0, x * cell, board.height() * cell);
      }
      for (int y = 0; y <= board.height(); y++) {
        g2.drawLine(0, y * cell, board.width() * cell, y * cell);
      }

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
      var snakesList = snakesSupplier.get();
      for (Snake s : snakesList) {
        var body = s.snapshot().toArray(new Position[0]);
        boolean alive = s.isAlive();
        int id = s.id();

        Color base;
        if (!alive) {
          base = new Color(130, 130, 130); // Color gris si la serpiente está muerta
        } else if (id == 1) {
          base = new Color(34, 139, 34);   // Verde esmeralda (Jugador 1)
        } else if (id == 2) {
          base = new Color(0, 140, 200);   // Azul cian (Jugador 2)
        } else {
          // Paleta visual distintiva para N serpientes
          float hue = ((id * 0.17f) % 1.0f);
          base = Color.getHSBColor(hue, 0.80f, 0.85f);
        }

        for (int i = 0; i < body.length; i++) {
          var p = body[i];
          int shade = Math.max(0, 30 - i * 3);
          if (alive) {
            g2.setColor(new Color(
                Math.min(255, base.getRed() + shade),
                Math.min(255, base.getGreen() + shade),
                Math.min(255, base.getBlue() + shade)));
          } else {
            g2.setColor(new Color(
                Math.max(50, base.getRed() - i * 2),
                Math.max(50, base.getGreen() - i * 2),
                Math.max(50, base.getBlue() - i * 2)));
          }
          g2.fillRect(p.x() * cell + 2, p.y() * cell + 2, cell - 4, cell - 4);
        }
      }
      g2.dispose();
    }
  }

  public static void launch() {
    SwingUtilities.invokeLater(SnakeApp::new);
  }
}
