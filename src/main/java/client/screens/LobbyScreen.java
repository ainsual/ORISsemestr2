package client.screens;

import client.MainApp;
import client.NetworkService;
import common.GameSettings;
import common.Message;
import common.MessageTypes;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class LobbyScreen extends BorderPane {
    private final MainApp app;
    private final NetworkService networkService;
    private final String playerId;
    private final Canvas previewCanvas;
    private final GraphicsContext gc;
    private final Label playersLabel;
    private final Label statusLabel;
    private final Label countdownLabel;
    private final Timeline countdownAnimation;

    private List<common.Player> players = new ArrayList<>();
    private double matchStartCountdown = 0;
    private boolean gameStarted = false;

    public LobbyScreen(MainApp app, NetworkService networkService, String playerId) {
        this.app = app;
        this.networkService = networkService;
        this.playerId = playerId;

        setPadding(new Insets(10));

        // Верхняя панель с информацией
        HBox topPanel = new HBox(10);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setStyle("-fx-padding: 10;");

        playersLabel = new Label("Игроков: 1");
        statusLabel = new Label("Ожидание игроков...");
        countdownLabel = new Label();
        countdownLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");

        topPanel.getChildren().addAll(playersLabel, statusLabel, countdownLabel);

        // Холст для предпросмотра игрового поля
        previewCanvas = new Canvas(GameSettings.WORLD_WIDTH / 2, GameSettings.WORLD_HEIGHT / 2);
        gc = previewCanvas.getGraphicsContext2D();

        // Нижняя панель с инструкциями
        VBox bottomPanel = new VBox(5);
        bottomPanel.setAlignment(Pos.CENTER);
        bottomPanel.setStyle("-fx-padding: 10;");

        Label instructions = new Label("Управление: WASD для движения\nЦель: стойте на пятне правильного цвета, когда время истечет");
        bottomPanel.getChildren().add(instructions);

        setTop(topPanel);
        setCenter(previewCanvas);
        setBottom(bottomPanel);

        // Анимация обратного отсчета до начала матча
        countdownAnimation = new Timeline(new KeyFrame(Duration.millis(100), e -> drawPreview()));
        countdownAnimation.setCycleCount(Timeline.INDEFINITE);
        countdownAnimation.play();
    }

    public void updateMatchStart(Message message) {
        matchStartCountdown = message.getMatchStartCountdown();
        gameStarted = message.isGameStarted();

        if (gameStarted) {
            statusLabel.setText("Игра начинается!");
            countdownLabel.setText(String.format("%.1f", matchStartCountdown));
        } else {
            statusLabel.setText("Ожидание игроков...");
            countdownLabel.setText("");
        }

        updatePlayerCount();
    }

    public void updateGameState(Message message) {
        players = message.getPlayers();
        matchStartCountdown = message.getMatchStartCountdown();
        gameStarted = message.isGameStarted();

        updatePlayerCount();

        if (gameStarted && message.getRound() == 1) {
            // Игра началась, переходим на игровой экран
            countdownAnimation.stop();
            app.showGameScreen();
        }
    }

    private void updatePlayerCount() {
        playersLabel.setText("Игроков: " + players.size());

        if (players.size() >= 2 && !gameStarted) {
            statusLabel.setText(String.format("Матч начнется через %.1f сек", matchStartCountdown));
            countdownLabel.setText(String.format("%.1f", matchStartCountdown));
        }
    }

    private void drawPreview() {
        // Очистка холста
        gc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

        // Отображение сетки пятен
        double scaleX = previewCanvas.getWidth() / GameSettings.WORLD_WIDTH;
        double scaleY = previewCanvas.getHeight() / GameSettings.WORLD_HEIGHT;
        double scale = Math.min(scaleX, scaleY);

        // Генерация случайных пятен для предпросмотра
        for (int y = 0; y < GameSettings.WORLD_HEIGHT; y += GameSettings.GRID_SIZE) {
            for (int x = 0; x < GameSettings.WORLD_WIDTH; x += GameSettings.GRID_SIZE) {
                // Простой алгоритм для генерации цветов
                int index = ((int)(x / GameSettings.GRID_SIZE) + (int)(y / GameSettings.GRID_SIZE)) %
                        common.GameSettings.ROUND_COLORS.length;
                String color = common.GameSettings.ROUND_COLORS[index];

                gc.setFill(Color.web(color));
                gc.fillRect(x * scale, y * scale, GameSettings.GRID_SIZE * scale, GameSettings.GRID_SIZE * scale);
            }
        }

        // Отображение игроков (как точки)
        for (common.Player player : players) {
            double px = player.getX() * scale;
            double py = player.getY() * scale;

            gc.setFill(player.getId().equals(playerId) ? Color.BLUE : Color.RED);
            gc.fillOval(px - 5, py - 5, 10, 10);
        }
    }
}