package client;

import client.screens.ConnectionScreen;
import client.screens.GameOverScreen;
import client.screens.GameScreen;
import client.screens.LobbyScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static common.MessageTypes.*;

public class MainApp extends Application {

    private Stage primaryStage;
    private Scene mainScene;

    private NetworkService networkService;

    private ConnectionScreen connectionScreen;
    private LobbyScreen lobbyScreen;
    private GameScreen gameScreen;
    private GameOverScreen gameOverScreen;

    private boolean isClosing = false;
    private boolean gameStarted = false; // Флаг начала игры

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.networkService = new NetworkService(this::handleServerMessage);

        connectionScreen = new ConnectionScreen(this, networkService);
        mainScene = new Scene(connectionScreen, 400, 300);

        primaryStage.setScene(mainScene);
        primaryStage.setTitle("ColorRush - Мультиплеерная игра");
        primaryStage.setResizable(false);

        primaryStage.setOnCloseRequest(e -> {
            isClosing = true;
            if (networkService != null) {
                networkService.disconnect();
            }
            Platform.exit();
            System.exit(0);
        });

        primaryStage.show();
    }

    private void handleServerMessage(common.Message message) {
        System.out.println("[CLIENT] Получено сообщение: " + message.getType());

        Platform.runLater(() -> {
            switch (message.getType()) {
                case CONNECT:
                    showLobbyScreen(message.getPlayerId());
                    break;
                case GAME_STATE:
                    handleGameState(message);
                    break;
                case ROUND_START:
                    handleRoundStart(message);
                    break;
                case GAME_OVER:
                    showGameOverScreen(message);
                    break;
                case MATCH_START:
                    handleMatchStart(message);
                    break;
            }
        });
    }

    private void handleGameState(common.Message message) {
        // Если игра началась, но игровые экран еще не отображен
        if (message.isGameStarted() && !gameStarted && (mainScene.getRoot() instanceof LobbyScreen || lobbyScreen != null)) {
            System.out.println("[CLIENT] Игра началась, переключаемся на игровой экран");
            showGameScreen();
            gameStarted = true;
        }

        if (gameScreen != null) {
            gameScreen.updateGameState(message);
        }
    }

    private void handleMatchStart(common.Message message) {
        System.out.println("[CLIENT] Получено сообщение MATCH_START");
        // Просто показываем игровой экран, если мы в лобби
        if (mainScene.getRoot() instanceof LobbyScreen || lobbyScreen != null) {
            System.out.println("[CLIENT] Переключение на игровой экран по MATCH_START");
            showGameScreen();
            gameStarted = true;
        }
    }

    private void handleRoundStart(common.Message message) {
        if (gameScreen != null) {
            gameScreen.handleRoundStart(message);
        } else {
            // Если раунд начался, но игровой экран еще не отображен
            System.out.println("[CLIENT] Раунд начался, показываем игровой экран");
            showGameScreen();
            if (gameScreen != null) {
                gameScreen.handleRoundStart(message);
            }
        }
    }

    public void showConnectionScreen() {
        cleanupCurrentScreen();
        gameStarted = false; // Сбрасываем флаг начала игры

        if (connectionScreen == null) {
            connectionScreen = new ConnectionScreen(this, networkService);
        }

        mainScene.setRoot(connectionScreen);

        primaryStage.setResizable(false);
        primaryStage.setWidth(400);
        primaryStage.setHeight(300);
        primaryStage.centerOnScreen();
    }

    public void showLobbyScreen(String playerId) {
        cleanupCurrentScreen();
        gameStarted = false; // Сбрасываем флаг начала игры

        lobbyScreen = new LobbyScreen(this, networkService, playerId);
        mainScene.setRoot(lobbyScreen);

        primaryStage.setResizable(false);
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.centerOnScreen();
    }

    public void showGameScreen() {
        cleanupCurrentScreen();

        gameScreen = new GameScreen(this, networkService);
        mainScene.setRoot(gameScreen);

        primaryStage.setResizable(true);
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.centerOnScreen();
    }

    public void showGameOverScreen(common.Message message) {
        cleanupCurrentScreen();
        gameStarted = false; // Сбрасываем флаг начала игры

        gameOverScreen = new GameOverScreen(this, message);
        mainScene.setRoot(gameOverScreen);
        primaryStage.sizeToScene();

        new Thread(() -> {
            networkService.disconnect();

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ignored) {}

            Platform.runLater(() -> {
                if (!isClosing) {
                    showConnectionScreen();
                }
            });
        }).start();
    }

    private void cleanupCurrentScreen() {
        if (gameScreen != null) {
            gameScreen.cleanup();
            gameScreen = null;
        }

        lobbyScreen = null;
        gameOverScreen = null;
    }

    public static void main(String[] args) {
        launch(args);
    }
}