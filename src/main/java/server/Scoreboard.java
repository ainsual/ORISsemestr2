package server;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class Scoreboard {
    private static final String SCORE_FILE = "scores.json";
    private final Map<String, Integer> scores = new ConcurrentHashMap<>();

    public Scoreboard() {
        loadScores();
    }

    public synchronized void addWin(String playerName, int round) {
        scores.put(playerName, scores.getOrDefault(playerName, 0) + round - 1);
        saveScores();
    }

    public List<ScoreboardEntry> getTopScores(int limit) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new ScoreboardEntry(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private void loadScores() {
        // Загрузка из ресурсов (для первого запуска)
        URL resourceUrl = getClass().getClassLoader().getResource(SCORE_FILE);
        File file = new File(SCORE_FILE);

        // Если файл не существует в рабочей директории, копируем из ресурсов
        if (!file.exists() && resourceUrl != null) {
            try {
                Path source = Paths.get(resourceUrl.toURI());
                Files.copy(source, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.err.println("Не удалось скопировать scores.json из ресурсов: " + e.getMessage());
            }
        }

        // Загрузка файла
        if (file.exists()) {
            try (Reader reader = new FileReader(file)) {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Integer>>(){}.getType();
                Map<String, Integer> loadedScores = gson.fromJson(reader, type);
                if (loadedScores != null) {
                    scores.putAll(loadedScores);
                }
            } catch (IOException e) {
                System.err.println("Ошибка загрузки рейтинга: " + e.getMessage());
            }
        }
    }

    private void saveScores() {
        File file = new File(SCORE_FILE);
        File dir = file.getParentFile();

        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }

        try (Writer writer = new FileWriter(file)) {
            Gson gson = new Gson();
            gson.toJson(scores, writer);
        } catch (IOException e) {
            System.err.println("Ошибка сохранения рейтинга: " + e.getMessage());
        }
    }

    public static class ScoreboardEntry {
        private String playerName;
        private int wins;

        public ScoreboardEntry() {}

        public ScoreboardEntry(String playerName, int wins) {
            this.playerName = playerName;
            this.wins = wins;
        }

        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public int getWins() { return wins; }
        public void setWins(int wins) { this.wins = wins; }
    }
}