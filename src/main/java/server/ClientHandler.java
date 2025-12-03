package server;

import common.Message;
import common.MessageTypes;
import common.Player;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final GameRoom gameRoom;
    private final Scoreboard scoreboard;
    private String playerId;
    private String playerName;
    private boolean running = true;

    private OutputStream outputStream;
    private InputStream inputStream;

    public ClientHandler(Socket socket, GameRoom gameRoom) {
        this.socket = socket;
        this.gameRoom = gameRoom;
        this.scoreboard = gameRoom.getScoreboard();
    }

    @Override
    public void run() {
        System.out.println("[SERVER][DEBUG] Starting client handler: " + socket.getInetAddress());
        try {
            // Use raw streams
            outputStream = socket.getOutputStream();
            inputStream = socket.getInputStream();

            System.out.println("[SERVER][DEBUG] I/O streams created");
            processMessages();
        } catch (Exception e) {
            System.err.println("[SERVER][ERROR] EXCEPTION: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            disconnect();
            System.out.println("[SERVER][DEBUG] Client fully disconnected: " + playerId);
        }
    }

    private void processMessages() throws IOException {
        byte[] buffer = new byte[4096];
        StringBuilder currentMessage = new StringBuilder();

        int bytesRead;
        while (running && (bytesRead = inputStream.read(buffer)) != -1) {
            String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            System.out.println("[SERVER][DEBUG] Received bytes: " + bytesRead);
            System.out.println("[SERVER][DEBUG] Raw data: [" + received + "]");

            currentMessage.append(received);

            // Process complete JSON objects (handling potential pretty-printed JSON)
            while (containsCompleteJson(currentMessage)) {
                String json = extractFirstJson(currentMessage);
                if (json != null && !json.isEmpty()) {
                    System.out.println("[SERVER][DEBUG] RECEIVED MESSAGE: " + json);
                    try {
                        Message message = Message.fromJson(json);
                        System.out.println("[SERVER][DEBUG] Message type: " + message.getType());
                        handleIncomingMessage(message);
                    } catch (Exception e) {
                        System.err.println("[SERVER][ERROR] JSON parsing error: " + e.getMessage());
                        System.err.println("[SERVER][DEBUG] Invalid JSON: " + json);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Safely checks if the buffer contains a complete JSON object
     * by counting braces and looking for newline termination
     */
    private boolean containsCompleteJson(StringBuilder buffer) {
        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        char prevChar = 0;

        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);

            // Handle string escaping
            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{') openBraces++;
                if (c == '}') closeBraces++;
            }

            prevChar = c;

            // Check for complete JSON object at newline boundaries
            if (c == '\n' && openBraces > 0 && openBraces == closeBraces) {
                return true;
            }
        }

        // Also check if we have a complete object without newline
        return openBraces > 0 && openBraces == closeBraces;
    }

    /**
     * Extracts the first complete JSON object from the buffer
     * Returns null if no complete JSON is found
     */
    private String extractFirstJson(StringBuilder buffer) {
        int openBraces = 0;
        int closeBraces = 0;
        boolean inString = false;
        char prevChar = 0;
        int jsonEnd = -1;

        for (int i = 0; i < buffer.length(); i++) {
            char c = buffer.charAt(i);

            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            }

            if (!inString) {
                if (c == '{') openBraces++;
                if (c == '}') closeBraces++;

                // Check for complete JSON object
                if (openBraces > 0 && openBraces == closeBraces) {
                    jsonEnd = i + 1;

                    // Check if followed by newline or end of buffer
                    if (i + 1 < buffer.length() && buffer.charAt(i + 1) == '\n') {
                        jsonEnd = i + 2; // Include the newline
                    }
                    break;
                }
            }

            prevChar = c;
        }

        if (jsonEnd == -1 || openBraces == 0 || openBraces != closeBraces) {
            return null;
        }

        String json = buffer.substring(0, jsonEnd).trim();
        // Remove the JSON object and any trailing newline
        buffer.delete(0, jsonEnd);
        return json.replaceAll("\\n$", "").trim();
    }

    private void handleIncomingMessage(Message message) {
        if (message == null || message.getType() == null) {
            System.out.println("[SERVER][DEBUG] Received null or invalid message");
            return;
        }

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
            default:
                System.out.println("[SERVER][DEBUG] Unknown message type: " + message.getType());
        }
    }

    private void handleConnect(Message message) {
        System.out.println("[SERVER][DEBUG] Handling CONNECT message");
        if (message.getPlayerName() == null || message.getPlayerName().trim().isEmpty()) {
            System.err.println("[SERVER][ERROR] Player name cannot be empty");
            return;
        }

        playerName = message.getPlayerName().trim();
        playerId = UUID.randomUUID().toString();
        System.out.println("[SERVER][DEBUG] New player: " + playerName + " (ID: " + playerId + ")");

        Player player = new Player(playerId, playerName);
        gameRoom.addPlayer(player);

        // Send connection confirmation
        Message response = new Message(MessageTypes.CONNECT);
        response.setPlayerId(playerId);
        response.setPlayerName(playerName);

        String jsonResponse = response.toJson();
        System.out.println("[SERVER][DEBUG] SENDING RESPONSE: " + jsonResponse);
        sendRawMessage(jsonResponse);
    }

    private void handleMove(Message message) {
        if (playerId != null) {
            System.out.println("[SERVER][DEBUG] Handling MOVE for player " + playerId + ": " + message.getX() + ", " + message.getY());
            gameRoom.handlePlayerMove(playerId, message.getX(), message.getY());
        }
    }

    private void sendRawMessage(String message) {
        try {
            if (!socket.isClosed() && outputStream != null) {
                // Add newline delimiter
                String messageWithNewline = message + "\n";
                byte[] bytes = messageWithNewline.getBytes(StandardCharsets.UTF_8);
                outputStream.write(bytes);
                outputStream.flush();
                System.out.println("[SERVER][DEBUG] Sent bytes: " + bytes.length);
            }
        } catch (Exception e) {
            System.err.println("[SERVER][ERROR] Send error: " + e.getMessage());
            disconnect();
        }
    }

    private void disconnect() {
        running = false;
        try {
            if (playerId != null) {
                System.out.println("[SERVER][DEBUG] Removing player from room: " + playerId);
                gameRoom.removePlayer(playerId);
            }
            if (socket != null && !socket.isClosed()) {
                System.out.println("[SERVER][DEBUG] Closing socket");
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting client: " + e.getMessage());
        }
    }
}