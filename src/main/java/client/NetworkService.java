package client;

import common.Message;
import common.MessageTypes;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class NetworkService {
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private Thread receiveThread;
    private boolean connected = false;
    private final Consumer<Message> messageHandler;

    public NetworkService(Consumer<Message> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public boolean connect(String host, int port) {
        System.out.println("[CLIENT]1 Попытка подключения к " + host + ":" + port);
        try {
            socket = new Socket(host, port);
            System.out.println("[CLIENT]2 Успешно подключен к серверу");

            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;

            System.out.println("[CLIENT]3 Потоки ввода/вывода созданы");

            receiveThread = new Thread(this::receiveMessages);
            receiveThread.setDaemon(true);
            receiveThread.start();

            System.out.println("[CLIENT]4 Поток приема сообщений good");
            return true;
        } catch (IOException e) {
            System.out.println("[CLIENT]5 ОШИБКА подключения: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        if (!connected) return;

        try {
            if (out != null) {
                Message msg = new Message(common.MessageTypes.DISCONNECT);
                out.writeUTF(msg.toJson());
                out.flush();
            }

            connected = false;
            if (receiveThread != null) {
                receiveThread.interrupt();
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при отключении: " + e.getMessage());
        }
    }

    public void sendConnect(String playerName) {
        if (!connected) return;
        try {
            Message msg = new Message(MessageTypes.CONNECT);
            System.out.println("server get message");
            msg.setPlayerName(playerName);
            out.writeUTF(msg.toJson());  // ← JSON через writeUTF()
            System.out.println("server formater message");
            out.flush();
        } catch (IOException e) {
            handleConnectionError(e);
        }
    }

    public void sendMove(double x, double y) {
        if (!connected) return;

        try {
            Message msg = new Message(common.MessageTypes.MOVE);
            msg.setX(x);
            msg.setY(y);
            out.writeUTF(msg.toJson());
            out.flush();
        } catch (IOException e) {
            handleConnectionError(e);
        }
    }

    private void receiveMessages() {
        while (connected && !Thread.currentThread().isInterrupted()) {
            try {
                String json = in.readUTF();
                Message message = Message.fromJson(json);
                Platform.runLater(() -> messageHandler.accept(message));
            } catch (IOException e) {
                if (connected) {
                    handleConnectionError(e);
                }
                break;
            }
        }
    }

    private void handleConnectionError(Exception e) {
        Platform.runLater(() -> {
            showAlert("Потеряно соединение",
                    "Соединение с сервером потеряно: " + e.getMessage());
            connected = false;
        });
    }

    private void showAlert(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public boolean isConnected() {
        return connected;
    }
}