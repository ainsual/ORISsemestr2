package server;

import common.GameSettings;
import common.Message;
import common.MessageTypes;
import common.Player;
import common.ScoreboardEntry;

import java.util.*;
import java.util.concurrent.*;

public class GameRoom {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Scoreboard scoreboard;

    // Состояние игры
    private int round = 0;
    private double roundTimeLeft;
    private double roundDuration;
    private String currentTargetColor;
    private boolean isRoundActive = false;
    private boolean gameStarted = false;
    private double matchStartCountdown = 1000;

    // Таймеры
    private ScheduledFuture<?> roundTimer;
    private ScheduledFuture<?> matchStartTimer;

    // Для рассылки обновлений
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public GameRoom(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
        //generateSpots();
    }

    // Регистрация клиента для рассылки обновлений
    public void registerClient(ClientHandler client) {
        clients.add(client);
        System.out.println("[ROOM] Зарегистрирован клиент для обновлений. Всего клиентов: " + clients.size());
    }

    public void unregisterClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("[ROOM] Удален клиент из обновлений. Всего клиентов: " + clients.size());
    }

//    private void generateSpots() {
//        spotColors.clear();
//        for (int i = 0; i < GameSettings.NUM_SPOTS; i++) {
//            spotColors.add(GameSettings.ROUND_COLORS[random.nextInt(GameSettings.ROUND_COLORS.length)]);
//        }
//    }

    public synchronized void addPlayer(Player player) {
        if (gameStarted) {
            return;
        }
        players.put(player.getId(), player);
        System.out.println("[ROOM] Добавлен игрок: " + player.getName() + " (ID: " + player.getId() + ")");
        System.out.println("[ROOM] Всего игроков: " + players.size());

        // Если набралось достаточно игроков и игра еще не начата
        if (players.size() >= 2 && !gameStarted) {
            startMatchCountdown();
        }

        // Отправляем обновление всем игрокам
        broadcastGameState();
    }

    public synchronized void removePlayer(String playerId) {
        Player removed = players.remove(playerId);
        if (removed != null) {
            System.out.println("[ROOM] Удален игрок: " + removed.getName());
        }

        // Если во время игры остался только один игрок
        if (gameStarted && players.size() < 2) {
            endGame(null);
        }

        // Отправляем обновление всем игрокам
        broadcastGameState();
    }

    private void startMatchCountdown() {
        if (matchStartTimer != null && !matchStartTimer.isDone()) {
            matchStartTimer.cancel(true);
        }

        matchStartCountdown = calculateMatchStartDelay();
        gameStarted = false;

        System.out.println("[ROOM] Запуск обратного отсчета до начала матча: " + String.format("%.1f", matchStartCountdown) + " сек");

        matchStartTimer = scheduler.scheduleAtFixedRate(() -> {
            matchStartCountdown -= 0.1;

            if (matchStartCountdown <= 0) {
                startGame();
                if (matchStartTimer != null) matchStartTimer.cancel(true);
            } else {
                broadcastGameState();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private double calculateMatchStartDelay() {
        if (matchStartCountdown == 1000) {
            return -0.1;
        }
        double delay = GameSettings.BASE_MATCH_START_DELAY -
                ((players.size() - 2) * GameSettings.PLAYER_DELAY_REDUCTION);
        return Math.max(delay, GameSettings.MIN_MATCH_START_DELAY);
    }

    private void startGame() {
        isRoundActive = false;
        currentTargetColor = "#FFFFF";
        gameStarted = true;
        System.out.println("[ROOM] Игра началась! Всего игроков: " + players.size());
        startNewRound(true);
    }

    private void startNewRound(boolean isStart) {
        round++;
        currentTargetColor = GameSettings.ROUND_COLORS[random.nextInt(GameSettings.ROUND_COLORS.length)];
        roundDuration = calculateRoundDuration();
        roundTimeLeft = roundDuration;
        isRoundActive = true;

        // Сброс позиций игроков
        for (Player player : players.values()) {
            player.setX(GameSettings.WORLD_WIDTH / 2);
            player.setY(GameSettings.WORLD_HEIGHT / 2);
            player.setAlive(true);
        }

        System.out.println("[ROOM] Раунд " + round + " начался. Цвет: " + currentTargetColor +
                ". Время: " + String.format("%.1f", roundDuration) + " сек");

        // Отправляем уведомление о начале раунда
        if (isStart) {
            broadcastGameStart();
        } else {
            broadcastRoundStart();
        }

        // Запуск таймера раунда
        if (roundTimer != null && !roundTimer.isDone()) {
            roundTimer.cancel(true);
        }

        roundTimer = scheduler.scheduleAtFixedRate(() -> {
            roundTimeLeft -= 0.1;

            if (roundTimeLeft <= 0) {
                endRound();
                if (roundTimer != null) roundTimer.cancel(true);
            } else {
                broadcastGameState();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private double calculateRoundDuration() {
        double duration = GameSettings.INITIAL_ROUND_TIME - ((round - 1) * GameSettings.ROUND_TIME_DECREMENT);
        return Math.max(duration, GameSettings.MIN_ROUND_TIME);
    }

    private void endRound() {
        isRoundActive = false;
        System.out.println("[ROOM] Раунд " + round + " завершен");

        // Проверка, кто остался на правильном цвете
        List<Player> survivors = new ArrayList<>();

        for (Player player : players.values()) {
            if (player.isAlive()) {
                String spotColor = getSpotColorAt(player.getX(), player.getY());
                System.out.println("[ROOM Цвет игрока " + player.getName() + spotColor );
                if (spotColor.equals(currentTargetColor)) {
                    survivors.add(player);
                    System.out.println("[ROOM] Игрок выжил: " + player.getName());
                } else {
                    player.setAlive(false);
                    System.out.println("[ROOM] Игрок выбыл: " + player.getName() +
                            " (стоял на " + spotColor + ", нужен " + currentTargetColor + ")");
                }
            }
        }

        broadcastGameState();

        // Задержка перед следующим раундом или завершением
        scheduler.schedule(() -> {
            if (survivors.size() <= 1) {
                Player winner = survivors.isEmpty() ? null : survivors.get(0);
                endGame(winner);
            } else {
                startNewRound(false);
            }
        }, 2000, TimeUnit.MILLISECONDS);
    }

    private void endGame(Player winner) {
        resetParamsGame();
        round = 0;
        currentTargetColor = "#FFFFFF";
        if (winner != null) {
            System.out.println("[ROOM] Игра завершена. Победитель: " + winner.getName());
            scoreboard.addWin(winner.getName());
        } else {
            System.out.println("[ROOM] Игра завершена. Ничья.");
        }

        broadcastGameOver(winner);
        // Сброс комнаты через 5 секунд

    }

    public void resetParamsGame() {
        gameStarted = false;
        isRoundActive = false;
        currentTargetColor = "#FFFFFF";
        System.out.println("[ROOM] Сброс комнаты");
        for (String playerId: players.keySet()) {
            removePlayer(playerId);
        }
        players.clear();


    }

    private String getSpotColorAt(double x, double y) {
        int gridX = (int) (x / GameSettings.GRID_SIZE);
        int gridY = (int) (y / GameSettings.GRID_SIZE);
        int index = (gridX + gridY) % GameSettings.ROUND_COLORS.length;
        return GameSettings.ROUND_COLORS[index];
    }

    public void handlePlayerMove(String playerId, double x, double y) {
        Player player = players.get(playerId);
        if (player != null && player.isAlive()) {
            // Ограничение движения в пределах поля
            double boundedX = Math.max(10, Math.min(x, GameSettings.WORLD_WIDTH - 10));
            double boundedY = Math.max(10, Math.min(y, GameSettings.WORLD_HEIGHT - 10));
            player.setX(boundedX);
            player.setY(boundedY);
            broadcastGameState();
        }
    }

    // Рассылка обновлений всем клиентам
    private void broadcastMessage(Message message) {
        for (ClientHandler client : new ArrayList<>(clients)) {
            try {
                client.sendMessage(message);
            } catch (Exception e) {
                System.err.println("[ROOM][ERROR] Ошибка отправки сообщения клиенту: " + e.getMessage());
                clients.remove(client);
            }
        }
    }

    private void broadcastGameState() {
        Message msg = new Message(MessageTypes.GAME_STATE);
        msg.setRound(round);
        msg.setTargetColor(currentTargetColor);
        msg.setTimeLeft(roundTimeLeft);
        msg.setDuration(roundDuration);
        msg.setGameStarted(gameStarted);
        msg.setIsRoundActive(isRoundActive);
        msg.setMatchStartCountdown(matchStartCountdown);

        // Передаем клонов для потокобезопасности
        List<Player> playerList = new ArrayList<>();
        for (Player player : players.values()) {
            playerList.add(player.clone());
        }
        msg.setPlayers(playerList);

        System.out.println("[ROOM][DEBUG] Отправка GAME_STATE. Раунд: " + round +
                ", Игроков: " + players.size() +
                ", Времени: " + String.format("%.1f", roundTimeLeft));

        broadcastMessage(msg);
    }

    private void broadcastRoundStart() {
        Message msg = new Message();
        msg.setTargetColor(currentTargetColor);
        msg.setDuration(roundDuration);
        broadcastMessage(msg);
    }

    private void broadcastGameStart() {
        Message msg = new Message(MessageTypes.MATCH_START);
        System.out.println("[ROOM] MATCH__START");
        msg.setTargetColor(currentTargetColor);
        msg.setDuration(roundDuration);
        broadcastMessage(msg);
    }

    private void broadcastGameOver(Player winner) {
        Message msg = new Message(MessageTypes.GAME_OVER);
        if (winner != null) {
            msg.setWinner(winner.getName());
        }

        // Получаем топ-10 игроков для отправки
        List<server.Scoreboard.ScoreboardEntry> topScores = scoreboard.getTopScores(10);
        ScoreboardEntry[] entries = new ScoreboardEntry[topScores.size()];

        for (int i = 0; i < topScores.size(); i++) {
            server.Scoreboard.ScoreboardEntry entry = topScores.get(i);
            entries[i] = new ScoreboardEntry(entry.getPlayerName(), entry.getWins());
        }

        msg.setScores(entries);
        broadcastMessage(msg);
    }
}