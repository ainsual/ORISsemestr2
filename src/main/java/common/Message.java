package common;

import client.screens.ScoreboardEntry;
import com.google.gson.Gson;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Message implements Serializable {
    private String type;
    private String playerId;
    private String playerName;
    private double x;
    private double y;
    private String color;
    private int round;
    private double timeLeft;
    private double duration;
    private boolean gameStarted;
    private boolean isRoundActive;
    private String winner;
    private ScoreboardEntry[] scores;
    private List<Player> players;
    private double matchStartCountdown;

    // Конструкторы
    public Message() {}

    public Message(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public double getX() {
        return x;
    }

    public void setX(double x) {
        this.x = x;
    }

    public double getY() {
        return y;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public double getTimeLeft() {
        return timeLeft;
    }

    public void setTimeLeft(double timeLeft) {
        this.timeLeft = timeLeft;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean isGameStarted() {
        return gameStarted;
    }

    public void setGameStarted(boolean gameStarted) {
        this.gameStarted = gameStarted;
    }

    public boolean isRoundActive() {
        return isRoundActive;
    }

    public void setRoundActive(boolean roundActive) {
        isRoundActive = roundActive;
    }

    public String getWinner() {
        return winner;
    }

    public void setWinner(String winner) {
        this.winner = winner;
    }

    public ScoreboardEntry[] getScores() {
        return scores;
    }

    public void setScores(ScoreboardEntry[] scores) {
        this.scores = scores;
    }

    public List<Player> getPlayers() {
        if (players == null) {
            players = new ArrayList<>(); // защита от NPE
        }
        return players;
    }

    // Сеттер для players
    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public double getMatchStartCountdown() {
        return matchStartCountdown;
    }

    public void setMatchStartCountdown(double matchStartCountdown) {
        this.matchStartCountdown = matchStartCountdown;
    }

    // Сериализация/десериализация
    public static Message fromJson(String json) {
        return new Gson().fromJson(json, Message.class);
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}