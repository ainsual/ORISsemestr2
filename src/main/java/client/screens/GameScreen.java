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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class GameScreen extends BorderPane {
    private final MainApp app;
    private final NetworkService networkService;
    private final Canvas gameCanvas;
    private final GraphicsContext gc;
    private final Label roundLabel;
    private final Label timerLabel;
    private final Label colorLabel;
    private final Label statusLabel;

    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Set<KeyCode> pressedKeys = ConcurrentHashMap.newKeySet();
    private String playerId;
    private int currentRound = 0;
    private double roundTimeLeft = 0;
    private double roundDuration = 0;
    private String currentTargetColor = "";
    private boolean isRoundActive = false;
    private boolean gameStarted = false;
    private double matchStartCountdown = 0;

    private double playerX = GameSettings.WORLD_WIDTH / 2;
    private double playerY = GameSettings.WORLD_HEIGHT / 2;
    private boolean isAlive = true;

    public GameScreen(MainApp app, NetworkService networkService) {
        this.app = app;
        this.networkService = networkService;

        setPadding(new Insets(10));

        // Игровой холст
        gameCanvas = new Canvas(GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);
        gc = gameCanvas.getGraphicsContext2D();

        // Панель статуса сверху
        HBox statusPanel = new HBox(15);
        statusPanel.setAlignment(Pos.CENTER);
        statusPanel.setStyle("-fx-padding: 5; -fx-background-color: #f0f0f0;");

        roundLabel = new Label("Раунд: 0");
        roundLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        timerLabel = new Label("Времени осталось: 0.0");
        timerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        timerLabel.setTextFill(Color.RED);

        colorLabel = new Label("Цвет: ...");
        colorLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        statusPanel.getChildren().addAll(roundLabel, timerLabel, colorLabel);

        // Нижняя панель со статусом
        statusLabel = new Label("Готовьтесь к раунду...");
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        statusLabel.setTextFill(Color.DARKGREEN);

        VBox bottomPanel = new VBox(statusLabel);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setStyle("-fx-padding: 10;");

        setTop(statusPanel);
        setCenter(gameCanvas);
        setBottom(bottomPanel);

        // Обработка клавиш
        gameCanvas.setFocusTraversable(true);
        gameCanvas.setOnKeyPressed(e -> pressedKeys.add(e.getCode()));
        gameCanvas.setOnKeyReleased(e -> pressedKeys.remove(e.getCode()));

        // Игровой цикл
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                updateGame();
                renderGame();
            }
        }.start();

        // Фокус на холсте
        gameCanvas.requestFocus();
    }

    public void updateGameState(Message message) {
        currentRound = message.getRound();
        roundTimeLeft = message.getTimeLeft();
        roundDuration = message.getDuration();
        currentTargetColor = message.getTargetColor() != null ?
                message.getTargetColor() : "#FFFFFF";
        isRoundActive = message.isIsRoundActive();
        gameStarted = message.isGameStarted();
        matchStartCountdown = message.getMatchStartCountdown();

        // Обновление позиций игроков, но локальный игрок не добавляется в общий список
        players.clear();
        if (message.getPlayers() != null) {
            for (Player player : message.getPlayers()) {
                // Добавляем в общий список ТОЛЬКО других игроков
                if (!player.getId().equals(playerId)) {
                    players.put(player.getId(), player);
                } else {
                    // Обновление локального игрока
                    playerX = player.getX();
                    playerY = player.getY();
                    isAlive = player.isAlive();
                }
            }
        }

        // Обновление UI
        roundLabel.setText("Раунд: " + currentRound);
        if (!gameStarted) {
            timerLabel.setText(String.format("Время до начала матча: %.1f", matchStartCountdown));
            timerLabel.setTextFill(Color.BLUE); // например, синий для ожидания
        } else if (isRoundActive) {
            timerLabel.setText(String.format("Времени до конца раунда: %.1f", roundTimeLeft));
            timerLabel.setTextFill(Color.RED); // красный — активный отсчёт
        }

        if (currentTargetColor != null && !currentTargetColor.isEmpty()) {
            colorLabel.setText("Цвет: " + currentTargetColor);
            colorLabel.setTextFill(Color.web(currentTargetColor));
        }

        if (!isRoundActive && gameStarted) {
            statusLabel.setText(isAlive ? "Вы выжили!" : "Вы проиграли!");
            statusLabel.setTextFill(isAlive ? Color.DARKGREEN : Color.DARKRED);
        } else if (!gameStarted) {
            statusLabel.setText("Ожидание начала матча...");
            statusLabel.setTextFill(Color.DARKBLUE);
        }
    }

    public void handleRoundStart(Message message) {
        currentTargetColor = message.getTargetColor();
        roundDuration = message.getDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;

        colorLabel.setText("Цвет: " + currentTargetColor);
        colorLabel.setTextFill(Color.web(currentTargetColor));
        statusLabel.setText("Встаньте на " + currentTargetColor);
        statusLabel.setTextFill(Color.DARKBLUE);
    }


    private void updateGame() {
        if (!isAlive || !isRoundActive || !gameStarted) return;

        // Обработка движения
        double dx = 0, dy = 0;

        if (pressedKeys.contains(KeyCode.W) || pressedKeys.contains(KeyCode.UP)) dy -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.S) || pressedKeys.contains(KeyCode.DOWN)) dy += GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.A) || pressedKeys.contains(KeyCode.LEFT)) dx -= GameSettings.MOVE_SPEED;
        if (pressedKeys.contains(KeyCode.D) || pressedKeys.contains(KeyCode.RIGHT)) dx += GameSettings.MOVE_SPEED;

        // Обновление позиции с ограничением границ
        playerX = Math.max(10, Math.min(playerX + dx, GameSettings.WORLD_WIDTH - 10));
        playerY = Math.max(10, Math.min(playerY + dy, GameSettings.WORLD_HEIGHT - 10));

        // Отправка позиции на сервер
        networkService.sendMove(playerX, playerY);
    }

    private void renderGame() {
        // Очистка холста
        gc.clearRect(0, 0, gameCanvas.getWidth(), gameCanvas.getHeight());

        // Отрисовка пятен
        drawSpots();

        // Отрисовка игроков
        drawPlayers();

        // Отрисовка границ
        gc.setStroke(Color.BLACK);
        gc.setLineWidth(2);
        gc.strokeRect(0, 0, GameSettings.WORLD_WIDTH, GameSettings.WORLD_HEIGHT);

        // Отображение текущего цвета в углу
        if (currentTargetColor != null && !currentTargetColor.isEmpty()) {
            gc.setFill(Color.web(currentTargetColor));
            gc.fillRect(GameSettings.WORLD_WIDTH - 60, 10, 50, 50);
            gc.setStroke(Color.BLACK);
            gc.strokeRect(GameSettings.WORLD_WIDTH - 60, 10, 50, 50);
        }
    }

    private void drawSpots() {
        // Генерация пятен на основе координат
        for (int y = 0; y < GameSettings.WORLD_HEIGHT; y += GameSettings.GRID_SIZE) {
            for (int x = 0; x < GameSettings.WORLD_WIDTH; x += GameSettings.GRID_SIZE) {
                // Алгоритм определения цвета пятна
                int gridX = (int)(x / GameSettings.GRID_SIZE);
                int gridY = (int)(y / GameSettings.GRID_SIZE);
                int index = (gridX + gridY) % GameSettings.ROUND_COLORS.length;
                String color = GameSettings.ROUND_COLORS[index];

                gc.setFill(Color.web(color));
                gc.fillRect(x, y, GameSettings.GRID_SIZE, GameSettings.GRID_SIZE);
            }
        }
    }

    private void drawPlayers() {
        // Сначала рисуем других игроков (красных)
        for (Player player : players.values()) {
            if (!player.getId().equals(playerId)) {
                gc.setFill(player.isAlive() ? Color.RED : Color.GRAY);
                gc.fillOval(player.getX() - 10, player.getY() - 10, 20, 20);
                gc.setStroke(Color.BLACK);
                gc.strokeOval(player.getX() - 10, player.getY() - 10, 20, 20);
            }
        }

        // Затем поверх них рисуем своего игрока (синего)
        gc.setFill(isAlive ? Color.BLUE : Color.GRAY);
        gc.fillOval(playerX - 10, playerY - 10, 20, 20);
        gc.setStroke(Color.BLACK);
        gc.strokeOval(playerX - 10, playerY - 10, 20, 20);
    }
}