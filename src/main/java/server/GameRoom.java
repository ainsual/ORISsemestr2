package server;

import client.screens.ScoreboardEntry;
import common.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

public class GameRoom {
    private final Map<String, Player> players = new ConcurrentHashMap<>();
    private final List<String> spotColors = new ArrayList<>();
    private final Random random = new Random();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Scoreboard scoreboard;

    private int round = 0;
    private double roundTimeLeft;
    private double roundDuration;
    private String currentTargetColor;
    private boolean isRoundActive = false;
    private boolean gameStarted = false;
    private double matchStartCountdown;

    private final Runnable roundTimerTask = this::updateRoundTimer;
    private final Runnable matchStartTask = this::updateMatchStartTimer;
    private ScheduledExecutorService roundTimer;
    private ScheduledExecutorService matchStartTimer;

    public GameRoom(Scoreboard scoreboard) {
        this.scoreboard = scoreboard;
        generateSpots();
    }

    private void generateSpots() {
        spotColors.clear();
        for (int i = 0; i < GameSettings.NUM_SPOTS; i++) {
            spotColors.add(GameSettings.ROUND_COLORS[random.nextInt(GameSettings.ROUND_COLORS.length)]);
        }
    }

    public synchronized void addPlayer(Player player) {
        players.put(player.getId(), player);
        if (players.size() >= 2 && !gameStarted) {
            startMatchCountdown();
        }
    }

    public synchronized void removePlayer(String playerId) {
        players.remove(playerId);
        if (gameStarted && players.size() < 2) {
            endGame(null); // Принудительное завершение при недостатке игроков
        }
    }

    private void startMatchCountdown() {
        matchStartCountdown = calculateMatchStartDelay();
        gameStarted = false;

        if (matchStartTimer != null) {
            matchStartTimer.shutdownNow();
        }

        matchStartTimer = Executors.newSingleThreadScheduledExecutor();
        matchStartTimer.scheduleAtFixedRate(matchStartTask, 0, 100, TimeUnit.MILLISECONDS);
    }

    private double calculateMatchStartDelay() {
        double delay = GameSettings.BASE_MATCH_START_DELAY -
                ((players.size() - 2) * GameSettings.PLAYER_DELAY_REDUCTION);
        return Math.max(delay, GameSettings.MIN_MATCH_START_DELAY);
    }

    private void updateMatchStartTimer() {
        matchStartCountdown -= 0.1;

        if (matchStartCountdown <= 0) {
            startGame();
            matchStartTimer.shutdown();
        } else {
            broadcastGameState();
        }
    }

    private void startGame() {
        gameStarted = true;
        round = 0;
        startNewRound();
    }

    private void startNewRound() {
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

        // Запуск таймера раунда
        if (roundTimer != null) {
            roundTimer.shutdownNow();
        }

        roundTimer = Executors.newSingleThreadScheduledExecutor();
        roundTimer.scheduleAtFixedRate(roundTimerTask, 0, 100, TimeUnit.MILLISECONDS);

        broadcastRoundStart();
    }

    private double calculateRoundDuration() {
        double duration = GameSettings.INITIAL_ROUND_TIME - ((round - 1) * GameSettings.ROUND_TIME_DECREMENT);
        return Math.max(duration, GameSettings.MIN_ROUND_TIME);
    }

    private void updateRoundTimer() {
        roundTimeLeft -= 0.1;

        if (roundTimeLeft <= 0) {
            endRound();
            roundTimer.shutdown();
        } else {
            broadcastGameState();
        }
    }

    private void endRound() {
        isRoundActive = false;

        // Проверка, кто остался на правильном цвете
        List<Player> survivors = new ArrayList<>();

        for (Player player : players.values()) {
            if (player.isAlive()) {
                String spotColor = getSpotColorAt(player.getX(), player.getY());
                if (spotColor.equals(currentTargetColor)) {
                    survivors.add(player);
                } else {
                    player.setAlive(false);
                }
            }
        }

        broadcastGameState();

        // Задержка перед следующим раундом или завершением
        scheduler.schedule(() -> {
            if (survivors.size() <= 1 || round >= 10) {
                endGame(survivors.isEmpty() ? null : survivors.get(0));
            } else {
                startNewRound();
            }
        }, 2, TimeUnit.SECONDS);
    }

    private void endGame(Player winner) {
        gameStarted = false;
        isRoundActive = false;

        if (winner != null) {
            scoreboard.addWin(winner.getName());
        }

        broadcastGameOver(winner);

        // Сброс комнаты через 5 секунд
        scheduler.schedule(this::resetRoom, 5, TimeUnit.SECONDS);
    }

    private void resetRoom() {
        players.clear();
        generateSpots();
    }

    private String getSpotColorAt(double x, double y) {
        int gridX = (int)(x / GameSettings.GRID_SIZE) % GameSettings.NUM_SPOTS;
        int gridY = (int)(y / GameSettings.GRID_SIZE) % GameSettings.NUM_SPOTS;
        int index = (gridX + gridY) % GameSettings.NUM_SPOTS;
        return spotColors.get(index);
    }

    public void handlePlayerMove(String playerId, double x, double y) {
        Player player = players.get(playerId);
        if (player != null && player.isAlive()) {
            // Ограничение движения в пределах поля
            player.setX(Math.max(0, Math.min(x, GameSettings.WORLD_WIDTH - 20)));
            player.setY(Math.max(0, Math.min(y, GameSettings.WORLD_HEIGHT - 20)));
            broadcastGameState();
        }
    }

    private void broadcastGameState() {
        Message msg = new Message(MessageTypes.GAME_STATE);
        msg.setRound(round);
        msg.setColor(currentTargetColor);
        msg.setTimeLeft(roundTimeLeft);
        msg.setDuration(roundDuration);
        msg.setGameStarted(gameStarted);
        msg.setRoundActive(isRoundActive);
        msg.setMatchStartCountdown(matchStartCountdown);
        List<Player> playerList = new ArrayList<>(players.values());
        msg.setPlayers(playerList);

        // Передаем клонов для потокобезопасности
        msg.setPlayers(new ArrayList<>());
        for (Player player : players.values()) {
            msg.getPlayers().add(player.clone());
        }

        broadcastMessage(msg);
    }

    private void broadcastRoundStart() {
        Message msg = new Message(MessageTypes.ROUND_START);
        msg.setColor(currentTargetColor);
        msg.setDuration(roundDuration);
        broadcastMessage(msg);
    }

    private void broadcastGameOver(Player winner) {
        Message msg = new Message(MessageTypes.GAME_OVER);
        if (winner != null) {
            msg.setWinner(winner.getName());
        }
        msg.setScores(scoreboard.getTopScores(10)
                .stream()
                .map(entry -> new ScoreboardEntry(entry.getPlayerName(), entry.getWins()))
                .toArray((IntFunction<ScoreboardEntry[]>) ScoreboardEntry[]::new));
        broadcastMessage(msg);
    }

    private void broadcastMessage(Message message) {
        // В реальной реализации здесь отправка сообщений всем клиентам
        // Для примера просто вывод в консоль
        System.out.println("Отправлено сообщение: " + message.getType());
    }

}