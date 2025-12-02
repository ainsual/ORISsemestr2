package server;

import common.Message;
import common.MessageTypes;
import common.Player;

import java.io.*;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameRoom gameRoom;
    private final Scoreboard scoreboard;
    private String playerId;
    private String playerName;
    private boolean running = true;

    private ObjectOutputStream out;
    private ObjectInputStream in;

    public ClientHandler(Socket socket, GameRoom gameRoom, Scoreboard scoreboard) {
        this.socket = socket;
        this.gameRoom = gameRoom;
        this.scoreboard = scoreboard;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            // Обработка сообщений
            while (running && !socket.isClosed()) {
                try {
                    String json = in.readUTF();
                    Message message = Message.fromJson(json);
                    handleIncomingMessage(message);
                } catch (IOException e) {
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка подключения клиента: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleIncomingMessage(Message message) {
        switch (message.getType()) {
            case MessageTypes.CONNECT:
                handleConnect(message);
                break;
            case MessageTypes.MOVE:
                handleMove(message);
                break;
            case MessageTypes.DISCONNECT:
                running = false;
                break;
        }
    }

    private void handleConnect(Message message) {
        playerName = message.getPlayerName();
        playerId = UUID.randomUUID().toString();

        Player player = new Player(playerId, playerName);
        gameRoom.addPlayer(player);

        // Отправка подтверждения подключения
        Message response = new Message(MessageTypes.CONNECT);
        response.setPlayerId(playerId);
        sendMessage(response);
    }

    private void handleMove(Message message) {
        if (playerId != null) {
            gameRoom.handlePlayerMove(playerId, message.getX(), message.getY());
        }
    }

    public void sendMessage(Message message) {
        try {
            if (!socket.isClosed() && out != null) {
                out.writeUTF(message.toJson());
                out.flush();
            }
        } catch (IOException e) {
            disconnect();
        }
    }

    private void disconnect() {
        running = false;
        try {
            if (playerId != null) {
                gameRoom.removePlayer(playerId);
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при отключении клиента: " + e.getMessage());
        }
    }
}