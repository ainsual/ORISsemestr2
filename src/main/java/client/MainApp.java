package client;


import client.screens.ConnectionScreen;
import client.screens.GameOverScreen;
import client.screens.GameScreen;
import client.screens.LobbyScreen;
import common.MessageTypes;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;


public class MainApp extends Application {
    private Stage primaryStage;
    private NetworkService networkService;
    private ConnectionScreen connectionScreen;
    private LobbyScreen lobbyScreen;
    private GameScreen gameScreen;
    private GameOverScreen gameOverScreen;

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.networkService = new NetworkService(this::handleServerMessage);

        showConnectionScreen();

        primaryStage.setTitle("ColorRush - Мультиплеерная игра");
        primaryStage.setResizable(false);
        primaryStage.setOnCloseRequest(e -> {
            if (networkService != null) {
                networkService.disconnect();
            }
            Platform.exit();
            System.exit(0);
        });
    }

    private void handleServerMessage(common.Message message) {
        // Обработка сообщений от сервера (как в предыдущем варианте)
        Platform.runLater(() -> {
            switch (message.getType()) {
                case common.MessageTypes.CONNECT:
                    showLobbyScreen(message.getPlayerId());
                    break;
                case common.MessageTypes.GAME_STATE:
                    updateGameState(message);
                    break;
                case common.MessageTypes.ROUND_START:
                    handleRoundStart(message);
                    break;
                case common.MessageTypes.GAME_OVER:
                    showGameOverScreen(message);
                    break;
                case common.MessageTypes.MATCH_START:
                    updateMatchStart(message);
                    break;
            }
        });
    }

    private void updateGameState(common.Message message) {
        Scene scene = primaryStage.getScene();
        if (scene == null) return;

        Object root = scene.getRoot();

        if (root instanceof GameScreen) {
            ((GameScreen) root).updateGameState(message);
        } else {
            showGameScreen();
            ((GameScreen) primaryStage.getScene().getRoot()).updateGameState(message);
        }
    }

    private void handleRoundStart(common.Message message) {
        Scene scene = primaryStage.getScene();
        if (scene == null) return;

        Object root = scene.getRoot();

        if (root instanceof GameScreen) {
            ((GameScreen) root).handleRoundStart(message);
        } else {
            showGameScreen();
            ((GameScreen) primaryStage.getScene().getRoot()).handleRoundStart(message);
        }
    }

    private void updateMatchStart(common.Message message) {
        Scene scene = primaryStage.getScene();
        if (scene == null) return;

        Object root = scene.getRoot();

        if (root instanceof LobbyScreen) {
            ((LobbyScreen) root).updateMatchStart(message);
        }
    }

    public void showConnectionScreen() {
        ConnectionScreen screen = new ConnectionScreen(this, networkService);
        primaryStage.setScene(new Scene(screen, 400, 300));
        primaryStage.show();
    }

    public void showLobbyScreen(String playerId) {
        LobbyScreen screen = new LobbyScreen(this, networkService, playerId);
        primaryStage.setScene(new Scene(screen, 800, 600));
        primaryStage.show();
    }

    public void showGameScreen() {
        GameScreen screen = new GameScreen(this, networkService);
        primaryStage.setScene(new Scene(screen, 800, 600));
        primaryStage.show();
    }

    public void showGameOverScreen(common.Message message) {
        GameOverScreen screen = new GameOverScreen(this, message);
        primaryStage.setScene(new Scene(screen, 800, 600));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}