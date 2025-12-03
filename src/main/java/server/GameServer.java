// JsonGameServer.java
package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Scoreboard scoreboard = new Scoreboard();

    public GameServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("JSON Game Server started on port " + port);

            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection: " + clientSocket.getInetAddress());
                GameRoom gameRoom = new GameRoom(scoreboard);
                ClientHandler handler = new ClientHandler(clientSocket, gameRoom);
                threadPool.submit(handler);
            }
        } catch (IOException e) {
            if (!serverSocket.isClosed()) {
                e.printStackTrace();
            }
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

    public static void main(String[] args) {
        new GameServer(5555).start();
    }
}