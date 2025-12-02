package client.screens;

import client.MainApp;
import common.Message;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.Arrays;

public class GameOverScreen extends BorderPane {
    private final MainApp app;

    public GameOverScreen(MainApp app, Message message) {
        this.app = app;

        setPadding(new Insets(20));

        // Заголовок
        Label titleLabel = new Label("Игра окончена");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 32));
        titleLabel.setAlignment(Pos.CENTER);

        // Определение победителя
        Label winnerLabel = new Label();
        winnerLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));

        if (message.getWinner() != null) {
            winnerLabel.setText("Победитель: " + message.getWinner());
            winnerLabel.setStyle("-fx-text-fill: #27ae60;");
        } else {
            winnerLabel.setText("Ничья!");
            winnerLabel.setStyle("-fx-text-fill: #f39c12;");
        }

        // Таблица рейтинга
        TableView<ScoreboardEntry> scoresTable = new TableView<>();
        scoresTable.setPrefWidth(400);

        TableColumn<ScoreboardEntry, String> nameCol = new TableColumn<>("Игрок");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("playerName"));
        nameCol.setPrefWidth(200);

        TableColumn<ScoreboardEntry, Integer> winsCol = new TableColumn<>("Побед");
        winsCol.setCellValueFactory(new PropertyValueFactory<>("wins"));
        winsCol.setPrefWidth(100);

        scoresTable.getColumns().addAll(nameCol, winsCol);

        // Заполнение таблицы
        if (message.getScores() != null) {
            Arrays.stream(message.getScores())
                    .map(entry -> new ScoreboardEntry(entry.getPlayerName(), entry.getWins()))
                    .forEach(scoresTable.getItems()::add);
        }

        // Кнопка возврата в лобби
        Button returnButton = new Button("Вернуться в главное меню");
        returnButton.setOnAction(e -> app.showConnectionScreen());
        returnButton.setStyle("-fx-font-size: 16px; -fx-padding: 10px 20px;");

        // Компоновка
        VBox centerBox = new VBox(20);
        centerBox.setAlignment(Pos.CENTER);
        centerBox.getChildren().addAll(titleLabel, winnerLabel, scoresTable, returnButton);

        setCenter(centerBox);
    }
}