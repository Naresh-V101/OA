package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartkneeoa.frontend.MainApp;
import com.smartkneeoa.frontend.ble.BleManager;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.format.DateTimeFormatter;
import java.util.*;

public class DashboardController extends BaseController {

    @FXML private Label  welcomeLabel;
    @FXML private Label  totalAssessments;
    @FXML private Label  latestRisk;
    @FXML private Label  sensorStatus;
    @FXML private Label  lastAssessmentDate;
    @FXML private Circle bleStatusDot;
    @FXML private Label  bleStatusLabel;

    // Recent history table
    @FXML private TableView<Map<String, String>>    recentHistoryTable;
    @FXML private TableColumn<Map<String, String>, String> colDate, colSide, colPain, colRisk, colStatus;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    @FXML
    public void initialize() {
        welcomeLabel.setText("Welcome, " + MainApp.getCurrentUserName());

        // Table columns
        colDate.setCellValueFactory(  r -> new SimpleStringProperty(r.getValue().get("date")));
        colSide.setCellValueFactory(  r -> new SimpleStringProperty(r.getValue().get("side")));
        colPain.setCellValueFactory(  r -> new SimpleStringProperty(r.getValue().get("pain")));
        colRisk.setCellValueFactory(  r -> new SimpleStringProperty(r.getValue().get("risk")));
        colStatus.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().get("status")));

        // BLE state listener
        BleManager.getInstance().addStateListener(state -> Platform.runLater(() -> {
            switch (state) {
                case CONNECTED    -> { bleStatusDot.setFill(Color.web("#4CAF50")); bleStatusLabel.setText("Connected");    sensorStatus.setText("Online"); }
                case SCANNING     -> { bleStatusDot.setFill(Color.web("#FF9800")); bleStatusLabel.setText("Scanning…");   sensorStatus.setText("Scanning"); }
                case DISCONNECTED -> { bleStatusDot.setFill(Color.web("#F44336")); bleStatusLabel.setText("Disconnected"); sensorStatus.setText("Offline"); }
                default           -> {}
            }
        }));

        loadDashboardData();
    }

    private void loadDashboardData() {
        Thread t = new Thread(() -> {
            try {
                JsonNode assessments = ApiService.getInstance().listAssessments(0, 5);
                Platform.runLater(() -> populateTable(assessments));
            } catch (Exception ex) {
                Platform.runLater(() -> totalAssessments.setText("—"));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void populateTable(JsonNode data) {
        ObservableList<Map<String, String>> rows = FXCollections.observableArrayList();
        int count = 0;

        if (data.isArray()) {
            count = data.size();
            for (JsonNode a : data) {
                Map<String, String> row = new LinkedHashMap<>();
                row.put("date",   a.path("assessmentDate").asText("—").replace("T", " ").substring(0, Math.min(16, a.path("assessmentDate").asText("—").length())));
                row.put("side",   a.path("kneeSide").asText("—"));
                row.put("pain",   a.path("painScore").asText("—") + " / 10");
                row.put("risk",   a.path("result").path("riskCategory").asText("Pending"));
                row.put("status", a.path("status").asText("—"));
                rows.add(row);
            }
        }

        recentHistoryTable.setItems(rows);
        totalAssessments.setText(String.valueOf(count));

        if (!rows.isEmpty()) {
            String risk = rows.get(0).get("risk");
            latestRisk.setText(risk);
            latestRisk.setStyle(riskColor(risk) + " -fx-font-size: 22px; -fx-font-weight: 700;");
            lastAssessmentDate.setText(rows.get(0).get("date"));
        }
    }

    @FXML
    private void connectSensor() {
        BleManager ble = BleManager.getInstance();
        if (ble.isConnected()) {
            ble.disconnect();
        } else {
            ble.connect();
        }
    }

    private String riskColor(String risk) {
        return switch (risk.toUpperCase()) {
            case "LOW"      -> "-fx-text-fill: #4CAF50;";
            case "MODERATE" -> "-fx-text-fill: #FF9800;";
            case "HIGH"     -> "-fx-text-fill: #F44336;";
            default         -> "-fx-text-fill: #8BAFC9;";
        };
    }
}
