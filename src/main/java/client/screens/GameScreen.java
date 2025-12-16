package client.screens;

import client.MainApp;
import client.NetworkService;
import common.*;
import javafx.animation.AnimationTimer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GameScreen extends BorderPane {

    private final MainApp app;
    private final NetworkService networkService;

    // Игровой холст
    private final Canvas gameCanvas;
    private final GraphicsContext gc;

    // Компас
    private final Canvas compassCanvas;
    private final GraphicsContext compassGc;
    private double compassAngle = 0; // текущее значение угла компаса
    private double playerAngle = 0;

    // UI
    private final Label roundLabel;
    private final Label timerLabel;
    private final Label colorLabel;
    private final Label statusLabel;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Set<KeyCode> pressedKeys = ConcurrentHashMap.newKeySet();

    private String playerId;
    private int currentRound;
    private double roundTimeLeft;
    private double roundDuration;
    private String currentTargetColor = "";

    private boolean isRoundActive;
    private boolean gameStarted;
    private boolean isAlive = true;

    private double playerX = GameSettings.WORLD_WIDTH / 2;
    private double playerY = GameSettings.WORLD_HEIGHT / 2;
    private byte[] field; // GRID_W * GRID_H

    public GameScreen(MainApp app, NetworkService networkService) {
        this.app = app;
        this.networkService = networkService;

        setPadding(new Insets(10));

        gameCanvas = new Canvas(GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
        gc = gameCanvas.getGraphicsContext2D();

        compassCanvas = new Canvas(100, 100);
        compassGc = compassCanvas.getGraphicsContext2D();

        HBox topPanel = new HBox(15);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0;");

        roundLabel = new Label("Раунд: 0");
        roundLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        timerLabel = new Label("Время: 0.0");
        timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        timerLabel.setTextFill(Color.RED);

        colorLabel = new Label("Цвет: -");
        colorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        topPanel.getChildren().addAll(roundLabel, timerLabel, colorLabel);

        statusLabel = new Label("Ожидание начала матча...");
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 18));

        VBox bottomPanel = new VBox(statusLabel);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setPadding(new Insets(10));

        BorderPane centerPane = new BorderPane();
        centerPane.setCenter(gameCanvas);
        centerPane.setTop(compassCanvas);
        BorderPane.setAlignment(compassCanvas, Pos.TOP_RIGHT);
        BorderPane.setMargin(compassCanvas, new Insets(10));

        setTop(topPanel);
        setCenter(centerPane);
        setBottom(bottomPanel);

        gameCanvas.setFocusTraversable(true);
        gameCanvas.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        gameCanvas.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateGame();
                renderGame();
            }
        }.start();

        gameCanvas.requestFocus();
    }

    public void updateGameState(Message message) {
        currentRound = message.getRound();
        roundTimeLeft = message.getTimeLeft();
        roundDuration = message.getDuration();
        currentTargetColor = message.getTargetColor();
        isRoundActive = message.isIsRoundActive();
        gameStarted = message.isGameStarted();
        if (message.getField() != null) {
            field = message.getField();
        }




        players.clear();
        if (message.getPlayers() != null) {
            for (Player p : message.getPlayers()) {
                if (!p.getId().equals(playerId)) {
                    players.put(p.getId(), p);
                } else {
                    playerX = p.getX();
                    playerY = p.getY();
                    isAlive = p.isAlive();
                }
            }
        }

        roundLabel.setText("Раунд: " + currentRound);

        if (gameStarted && isRoundActive) {
            timerLabel.setText(String.format("Время: %.1f", roundTimeLeft));
            timerLabel.setTextFill(Color.RED);
        }

        if (currentTargetColor != null) {
            colorLabel.setText("Цвет: " + currentTargetColor);
            colorLabel.setTextFill(Color.web(currentTargetColor));
        }
    }

    private void updateGame() {
        if (!gameStarted || !isRoundActive || !isAlive) return;

        double dx = 0;
        double dy = 0;

        if (pressedKeys.contains(KeyCode.W) || pressedKeys.contains(KeyCode.UP)) dy -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN)) dy += GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT)) dx -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT)) dx += GameSettings.MOVE_SPEED;

        if (dx != 0 || dy != 0) {
            double targetAngle = Math.atan2(dy, dx);
            // Сглаживание: двигаем compassAngle к targetAngle на 0.1 радиана за тик
            compassAngle = smoothAngle(compassAngle, targetAngle, 0.1);
        }



        playerX = Math.max(10, Math.min(playerX + dx, GameSettings.WORLD_WIDTH - 10));
        playerY = Math.max(10, Math.min(playerY + dy, GameSettings.WORLD_HEIGHT - 10));

        networkService.sendMove(playerX, playerY);
    }

    private void renderGame() {
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        drawSpots();
        drawPlayers();
        drawCompass();

        gc.setStroke(Color.BLACK);
        gc.strokeRect(0, 0, GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
    }

    private void drawSpots() {
        if (field == null) {
            System.out.println("no spots");
            return;
        }
        int w = GameSettings.GRID_W;

        for (int y = 0; y < GameSettings.GRID_H; y++) {
            for (int x = 0; x < GameSettings.GRID_W; x++) {
                int idx = field[y * w + x];
                gc.setFill(Color.web(GameSettings.ROUND_COLORS[idx]));
                gc.fillRect(
                        x * GameSettings.CELL_SIZE,
                        y * GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE,
                        GameSettings.CELL_SIZE
                );
            }
        }
    }


    private void drawPlayers() {
        for (Player p : players.values()) {
            gc.setFill(p.isAlive() ? Color.RED : Color.GRAY);
            gc.fillOval(p.getX() - 10, p.getY() - 10, 20, 20);
        }

        gc.setFill(isAlive ? Color.BLUE : Color.GRAY);
        gc.fillOval(playerX - 10, playerY - 10, 20, 20);
    }

    private void drawCompass() {
        double w = compassCanvas.getWidth();
        double h = compassCanvas.getHeight();
        double cx = w / 2;
        double cy = h / 2;
        double r = w / 2 - 10;

        // Очищаем холст
        compassGc.clearRect(0, 0, w, h);

        // Рисуем круг компаса
        compassGc.setStroke(Color.BLACK);
        compassGc.setLineWidth(2);
        compassGc.strokeOval(5, 5, w - 10, h - 10);

        // Плавное приближение compassAngle к playerAngle
        compassAngle = smoothAngle(compassAngle, playerAngle, 0.1);

        // Вычисляем конец стрелки
        double x = cx + Math.cos(compassAngle) * r;
        double y = cy + Math.sin(compassAngle) * r;

        // Рисуем стрелку
        compassGc.setStroke(Color.BLUE);
        compassGc.setLineWidth(3);
        compassGc.strokeLine(cx, cy, x, y);

        // Рисуем центр компаса
        compassGc.setFill(Color.BLACK);
        compassGc.fillOval(cx - 4, cy - 4, 8, 8);
    }

    // Метод для плавного приближения угла
    private double smoothAngle(double current, double target, double maxStep) {
        double diff = target - current;

        // корректировка разницы в диапазон [-PI, PI]
        while (diff < -Math.PI) diff += 2 * Math.PI;
        while (diff > Math.PI) diff -= 2 * Math.PI;

        if (Math.abs(diff) <= maxStep) {
            return target; // достигли цели
        } else {
            return current + Math.signum(diff) * maxStep;
        }
    }



    public void cleanup() {
        gameCanvas.setOnKeyPressed(null);
        gameCanvas.setOnKeyReleased(null);
        pressedKeys.clear();
    }
    public void handleRoundStart(Message message) {
        currentTargetColor = message.getTargetColor();
        roundDuration = message.getDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;
        field = message.getField();
        statusLabel.setText("Играем");
    }

}
