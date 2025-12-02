package server;

import common.Message;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private int nextPlayerId = 1;

    public GameServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Сервер запущен на порту " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void shutdown() {
        try {
            serverSocket.close();
            threadPool.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private ObjectOutputStream out;
        private ObjectInputStream in;
        private String playerId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Регистрация нового игрока
                playerId = "player_" + nextPlayerId++;
                clients.put(playerId, this);
                System.out.println("Зарегистрирован игрок: " + playerId);

                // Отправка начального состояния
                Message message = new Message("PLAYER_CONNECTED");
                message.setPlayerId(playerId);
                message.setX(100);
                message.setY(100);
                broadcast(message);

                // Обработка сообщений
                while (true) {
                    Message msg = (Message) in.readObject();
                    switch (msg.getType()) {
                        case "MOVE":
                            msg.setPlayerId(playerId);
                            broadcast(msg);
                            break;
                        case "DISCONNECT":
                            return;
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Клиент отключен: " + playerId);
            } finally {
                disconnect();
            }
        }

        private void broadcast(Message message) {
            for (ClientHandler client : clients.values()) {
                try {
                    client.out.writeObject(message);
                    client.out.flush();
                } catch (IOException e) {
                    // Удаление отключенных клиентов
                }
            }
        }

        private void disconnect() {
            try {
                if (playerId != null) {
                    clients.remove(playerId);
                    Message message = new Message("PLAYER_DISCONNECTED");
                    message.setPlayerId(playerId);
                    broadcast(message);
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        new GameServer(5555).start();
    }
}