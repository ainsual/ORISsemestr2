package client.screens;

public class ScoreboardEntry {
    private final String playerName;
    private final int wins;

    public ScoreboardEntry(String playerName, int wins) {
        this.playerName = playerName;
        this.wins = wins;
    }

    public String getPlayerName() { return playerName; }
    public int getWins() { return wins; }
}