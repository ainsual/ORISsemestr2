package client.screens;

import client.MainApp;
import client.NetworkService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class ConnectionScreen extends VBox {
    private final MainApp app;
    private final NetworkService networkService;
    private final TextField ipField;
    private final TextField portField;
    private final TextField nameField;
    private final Button connectButton;
    private final Label statusLabel;

    public ConnectionScreen(MainApp app, NetworkService networkService) {
        this.app = app;
        this.networkService = networkService;

        setPadding(new Insets(20));
        setSpacing(10);
        setAlignment(Pos.CENTER);

        Label titleLabel = new Label("Подключение к серверу ColorRush");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        ipField = new TextField("localhost");
        ipField.setPromptText("IP адрес сервера");

        portField = new TextField("5555");
        portField.setPromptText("Порт сервера");

        nameField = new TextField();
        nameField.setPromptText("Ваше имя");

        connectButton = new Button("Подключиться");
        connectButton.setOnAction(e -> connectToServer());

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: red;");

        getChildren().addAll(titleLabel, ipField, portField, nameField, connectButton, statusLabel);
    }

    private void connectToServer() {
        String ip = ipField.getText().trim();
        String portText = portField.getText().trim();
        String name = nameField.getText().trim();

        if (name.isEmpty()) {
            statusLabel.setText("Введите имя игрока");
            return;
        }

        if (name.length() > 15) {
            statusLabel.setText("Имя слишком длинное (макс. 15 символов)");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
            if (port < 1024 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            statusLabel.setText("Неверный номер порта");
            return;
        }

        statusLabel.setText("Подключение...");
        connectButton.setDisable(true);

        // Подключение в отдельном потоке
        new Thread(() -> {
            boolean connected = networkService.connect(ip, port);
            if (connected) {
                networkService.sendConnect(name);
            } else {
                javafx.application.Platform.runLater(() -> {
                    connectButton.setDisable(false);
                    statusLabel.setText("Не удалось подключиться");
                });
            }
        }).start();
    }
}