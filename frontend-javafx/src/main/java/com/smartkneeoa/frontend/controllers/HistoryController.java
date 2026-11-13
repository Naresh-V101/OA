package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartkneeoa.frontend.controllers.ResultsController;
import com.smartkneeoa.frontend.MainApp;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.LocalDate;
import java.util.*;

public class HistoryController extends BaseController {

    @FXML private DatePicker fromDate, toDate;
    @FXML private Label      resultCount;

    @FXML private TableView<JsonNode>     historyTable;
    @FXML private TableColumn<JsonNode, String> colId, colDate, colSide, colPain, colRisk, colStatus;
    @FXML private TableColumn<JsonNode, Void>   colAction;

    @FXML private Pagination pagination;

    private static final int PAGE_SIZE = 10;
    private final ObservableList<JsonNode> allRows = FXCollections.observableArrayList();
    private final ObservableList<JsonNode> pageRows = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("id").asText()));
        colDate.setCellValueFactory(r -> {
            String raw = r.getValue().path("assessmentDate").asText("—");
            return new SimpleStringProperty(raw.replace("T", " ").substring(0, Math.min(16, raw.length())));
        });
        colSide.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("kneeSide").asText("—")));
        colPain.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("painScore").asText("—") + "/10"));
        colRisk.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("result").path("riskCategory").asText("Pending")));
        colStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("status").asText("—")));

        // Action column – "View" button per row
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("View Results");
            { btn.setStyle("-fx-background-color:#00BFA5;-fx-text-fill:#0D1B2A;-fx-font-size:11px;-fx-background-radius:6;-fx-padding:5 12;-fx-cursor:hand;");
              btn.setOnAction(e -> {
                JsonNode row = getTableView().getItems().get(getIndex());
                long aid = row.path("id").asLong();
                ResultsController.targetAssessmentId = aid;
                try { MainApp.showHistory(); /* then show results */ MainApp.showResults(aid); } catch (Exception ex) { ex.printStackTrace(); }
              });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        historyTable.setItems(pageRows);

        pagination.currentPageIndexProperty().addListener((obs, old, newIdx) ->
            showPage(newIdx.intValue()));

        loadHistory();
    }

    @FXML
    private void refresh() { loadHistory(); }

    @FXML
    private void applyFilter() {
        // Client-side filter (server-side endpoint can be added for large datasets)
        LocalDate from = fromDate.getValue();
        LocalDate to   = toDate.getValue();
        List<JsonNode> filtered = allRows.stream().filter(a -> {
            String raw = a.path("assessmentDate").asText("").substring(0, 10);
            try {
                LocalDate d = LocalDate.parse(raw);
                boolean afterFrom = from == null || !d.isBefore(from);
                boolean beforeTo  = to   == null || !d.isAfter(to);
                return afterFrom && beforeTo;
            } catch (Exception e) { return true; }
        }).toList();

        resultCount.setText(filtered.size() + " result(s)");
        showFiltered(filtered);
    }

    @FXML
    private void clearFilter() {
        fromDate.setValue(null);
        toDate.setValue(null);
        resultCount.setText("");
        showFiltered(allRows);
    }

    private void loadHistory() {
        Thread t = new Thread(() -> {
            try {
                JsonNode resp = ApiService.getInstance().listAssessments(0, 200);
                Platform.runLater(() -> {
                    allRows.clear();
                    if (resp.isArray()) resp.forEach(allRows::add);
                    showFiltered(allRows);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> resultCount.setText("Error: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void showFiltered(List<JsonNode> rows) {
        int pages = Math.max(1, (int) Math.ceil(rows.size() / (double) PAGE_SIZE));
        pagination.setPageCount(pages);
        showPage(0, rows);
        resultCount.setText(rows.size() + " record(s)");
    }

    private void showPage(int page) {
        showPage(page, allRows);
    }

    private void showPage(int page, List<JsonNode> source) {
        int from = page * PAGE_SIZE;
        int to   = Math.min(from + PAGE_SIZE, source.size());
        pageRows.setAll(source.subList(from, to));
    }
}
