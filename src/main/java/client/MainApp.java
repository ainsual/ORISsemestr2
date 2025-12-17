package client;

import client.screens.ConnectionScreen;
import client.screens.GameOverScreen;
import client.screens.GameScreen;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import static common.MessageTypes.*;

public class MainApp extends Application {

    private Stage primaryStage;
    private Scene mainScene;

    private NetworkService networkService;

    // Убираем поля для хранения экранов - будем создавать новые экземпляры каждый раз
    private boolean isClosing = false;
    private boolean gameStarted = false;
    private boolean showCompass = true; // Значение по умолчанию

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        this.networkService = new NetworkService(this::handleServerMessage);

        showConnectionScreen();

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
        Platform.runLater(() -> {
            switch (message.getType()) {
                case CONNECT:
                    showGameScreen(message.getPlayerId());
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
        if (message.isGameStarted() && !gameStarted) {
            gameStarted = true;
        }

        if (primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof GameScreen) {
            ((GameScreen) primaryStage.getScene().getRoot()).updateGameState(message);
        }
    }

    private void handleMatchStart(common.Message message) {
        if (primaryStage.getScene() != null && !(primaryStage.getScene().getRoot() instanceof GameScreen)) {
            showGameScreen(null);
        }
    }

    private void handleRoundStart(common.Message message) {
        if (primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof GameScreen) {
            ((GameScreen) primaryStage.getScene().getRoot()).handleRoundStart(message);
        } else {
            showGameScreen(null);
            if (primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof GameScreen) {
                ((GameScreen) primaryStage.getScene().getRoot()).handleRoundStart(message);
            }
        }
    }

    public void showConnectionScreen() {
        // Всегда создаем НОВЫЙ экземпляр
        ConnectionScreen newConnectionScreen = new ConnectionScreen(this, networkService);

        Scene connectionScene = new Scene(newConnectionScreen, 400, 300);
        primaryStage.setScene(connectionScene);
        primaryStage.setResizable(false);
        centerStage();
        primaryStage.setWidth(400);
        primaryStage.setHeight(300);
        primaryStage.centerOnScreen();

        gameStarted = false;
        System.out.println("[APP] Показан экран подключения");
    }

    public void showGameScreen(String playerId) {
        GameScreen newGameScreen = new GameScreen(this, networkService);
        networkService.setPlayerId(playerId);

        // Получаем значение чекбокса компаса из ConnectionScreen, если он существует
        if (primaryStage.getScene() != null && primaryStage.getScene().getRoot() instanceof ConnectionScreen) {
            ConnectionScreen connectionScreen = (ConnectionScreen) primaryStage.getScene().getRoot();
            newGameScreen.setShowCompass(connectionScreen.getShowCompass());
        } else {
            newGameScreen.setShowCompass(showCompass);
        }

        if (playerId != null) {
            newGameScreen.setPlayerId(playerId);
        }

        Scene gameScene = new Scene(newGameScreen, 800, 600);
        primaryStage.setScene(gameScene);
        primaryStage.setFullScreen(true);


        // Принудительно устанавливаем фокус через небольшую задержку
        Platform.runLater(() -> {
            newGameScreen.requestFocus();
            System.out.println("[APP] Фокус установлен на игровом экране");
        });

        gameStarted = true;
        System.out.println("[APP] Показан игровой экран");
    }

    public void showGameOverScreen(common.Message message) {
        // Всегда создаем НОВЫЙ экземпляр
        GameOverScreen newGameOverScreen = new GameOverScreen(this, message);

        Scene gameOverScene = new Scene(newGameOverScreen, 800, 600);
        primaryStage.setScene(gameOverScene);
        primaryStage.setResizable(false);
        centerStage();
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.centerOnScreen();

        gameStarted = false;
        System.out.println("[APP] Показан экран окончания игры");

        new Thread(() -> {
            if (networkService != null) {
                networkService.disconnect();
            }
        }).start();
    }

    private void centerStage() {
        Platform.runLater(() -> {
            primaryStage.sizeToScene();
            primaryStage.centerOnScreen();
        });
    }


    public static void main(String[] args) {
        launch(args);
    }
}