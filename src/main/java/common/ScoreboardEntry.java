package common;

public class ScoreboardEntry {
    private String playerName;
    private int wins;

    public ScoreboardEntry() {}

    public ScoreboardEntry(String playerName, int wins) {
        this.playerName = playerName;
        this.wins = wins;
    }

    // Геттеры и сеттеры
    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }
    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }
}