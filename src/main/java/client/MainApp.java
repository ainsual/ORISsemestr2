package client;

import client.controllers.ConnectionController;
import client.controllers.GameController;
import client.controllers.GameOverController;
import common.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

import static common.MessageTypes.*;

public class MainApp extends Application {

    private Stage primaryStage;
    private NetworkService networkService;

    // Текущие контроллеры для доступа к их методам
    private ConnectionController connectionController;
    private GameController gameController;
    private GameOverController gameOverController;

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

    private void handleServerMessage(Message message) {
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

    private void handleGameState(Message message) {
        if (message.isGameStarted() && !gameStarted) {
            gameStarted = true;
        }

        // Обновляем игровое состояние только если GameController активен
        if (gameController != null) {
            gameController.updateGameState(message);
        }
    }

    private void handleMatchStart(Message message) {
        // Если мы не в игре - показываем игровой экран
        if (gameController == null) {
            showGameScreen(null);
        }
    }

    private void handleRoundStart(Message message) {
        // Если GameController активен - обрабатываем начало раунда
        if (gameController != null) {
            gameController.handleRoundStart(message);
        } else {
            // Иначе показываем игровой экран и затем обрабатываем сообщение
            showGameScreen(null);
            if (gameController != null) {
                gameController.handleRoundStart(message);
            }
        }
    }

    public void showConnectionScreen() {
        try {
            // ЯВНО отключаем полноэкранный режим перед сменой сцены
            if (primaryStage.isFullScreen()) {
                primaryStage.setFullScreen(false);
            }

            // ЯВНО сбрасываем параметры окна
            primaryStage.setResizable(false);

            // Загружаем FXML и получаем контроллер
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/connection_screen.fxml"));
            Parent root = loader.load();

            connectionController = loader.getController();
            connectionController.setMainApp(this);
            connectionController.setNetworkService(networkService);

            // Создаем новую сцену с ПРАВИЛЬНЫМИ ПАРАМЕТРАМИ
            Scene scene = new Scene(root);

            // Устанавливаем фиксированный размер ПОСЛЕ создания сцены
            primaryStage.setScene(scene);
            primaryStage.setWidth(400);
            primaryStage.setHeight(300);

            // Центрируем окно
            primaryStage.centerOnScreen();

            // Дополнительная гарантия правильного отображения
            Platform.runLater(() -> {
                primaryStage.sizeToScene();
            });

            // Сбрасываем другие контроллеры
            gameController = null;
            gameOverController = null;

            gameStarted = false;
            System.out.println("[APP] Показан экран подключения");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка загрузки FXML для экрана подключения");
        }
    }

    public void showGameScreen(String playerId) {
        try {
            // Загружаем FXML и получаем контроллер
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/game_screen.fxml"));
            Parent root = loader.load();

            gameController = loader.getController();
            gameController.setMainApp(this);
            gameController.setNetworkService(networkService);
            gameController.setPlayerId(playerId);

            // Получаем значение компаса из предыдущего экрана
            if (connectionController != null) {
                gameController.setShowCompass(connectionController.getShowCompass());
            } else {
                gameController.setShowCompass(showCompass);
            }

            // Создаем новую сцену
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);

            // Вход в полноэкранный режим
            primaryStage.setFullScreen(true);

            // Устанавливаем фокус на игровое поле
            Platform.runLater(() -> {
                if (gameController != null) {
                    gameController.requestFocusOnGameCanvas();
                    System.out.println("[APP] Фокус установлен на игровом экране");
                }
            });

            // Сбрасываем другие контроллеры
            connectionController = null;
            gameOverController = null;

            gameStarted = true;
            System.out.println("[APP] Показан игровой экран");
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка загрузки FXML для игрового экрана");
        }
    }

    public void showGameOverScreen(Message message) {
        try {
            // ЯВНО отключаем полноэкранный режим
            if (primaryStage.isFullScreen()) {
                primaryStage.setFullScreen(false);
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/views/game_over_screen.fxml"));
            Parent root = loader.load();

            gameOverController = loader.getController();
            gameOverController.setMainApp(this);
            gameOverController.setMessageData(message);

            // Создаем новую сцену
            Scene scene = new Scene(root);
            primaryStage.setScene(scene);

            // Устанавливаем фиксированный размер
            primaryStage.setResizable(false);
            primaryStage.setWidth(800);
            primaryStage.setHeight(600);
            primaryStage.centerOnScreen();

            // Сбрасываем другие контроллеры
            connectionController = null;
            gameController = null;

            gameStarted = false;
            System.out.println("[APP] Показан экран окончания игры");

            // Автоматический возврат к экрану подключения через 5 секунд
            new Thread(() -> {
                if (networkService != null) {
                    networkService.disconnect();
                }

                try {
                    Thread.sleep(5000); // 5 секунд на экране окончания
                } catch (InterruptedException ignored) {}

                Platform.runLater(() -> {
                    if (!isClosing) {
                        showConnectionScreen();
                    }
                });
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Ошибка загрузки FXML для экрана окончания игры");
        }
    }

    public void setCompassEnabled(boolean enabled) {
        this.showCompass = enabled;
    }

    public NetworkService getNetworkService() {
        return networkService;
    }

    public static void main(String[] args) {
        launch(args);
    }
}