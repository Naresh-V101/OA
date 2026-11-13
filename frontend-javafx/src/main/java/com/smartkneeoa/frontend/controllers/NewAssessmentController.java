package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartkneeoa.frontend.MainApp;
import com.smartkneeoa.frontend.ble.BleManager;
import com.smartkneeoa.frontend.models.SensorData;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class NewAssessmentController extends BaseController {

    // Step panes
    @FXML private VBox  step1Pane, step2Pane;
    @FXML private Label step1Label, step2Label, step3Label;

    // Step 1 fields
    @FXML private ComboBox<String> kneeSide;
    @FXML private Slider           painSlider;
    @FXML private Label            painScoreLabel;
    @FXML private TextArea         symptomsField;

    // Step 2 controls
    @FXML private Label       esp32Status;
    @FXML private Button      startCollectionBtn, stopCollectionBtn, analyzeBtn;
    @FXML private ProgressBar collectionProgress;
    @FXML private Label       collectionStatus, readingCountLabel;

    @FXML private Label errorLabel;

    // State
    private long   currentAssessmentId = -1;
    private int    collectionDurationSec = 30;
    private int    readingCount = 0;
    private boolean collecting = false;

    private ScheduledExecutorService collectionExecutor;
    private Consumer<SensorData> dataListener;
    private final List<SensorData> collectedReadings = new ArrayList<>();

    @FXML
    public void initialize() {
        kneeSide.setItems(FXCollections.observableArrayList("RIGHT", "LEFT", "BOTH"));
        kneeSide.setValue("RIGHT");

        painSlider.valueProperty().addListener((obs, old, val) ->
            painScoreLabel.setText(String.valueOf(val.intValue())));

        checkBleStatus();
    }

    // ── Navigation between steps ─────────────────────────────────
    @FXML
    private void proceedToStep2() {
        if (kneeSide.getValue() == null) {
            errorLabel.setText("Please select a knee side.");
            return;
        }

        errorLabel.setText("Creating assessment…");

        Thread t = new Thread(() -> {
            try {
                JsonNode resp = ApiService.getInstance().createAssessment(
                        kneeSide.getValue(),
                        (int) painSlider.getValue(),
                        symptomsField.getText().trim()
                );
                currentAssessmentId = resp.get("id").asLong();
                Platform.runLater(() -> {
                    errorLabel.setText("");
                    showStep(2);
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                    errorLabel.setText("Failed to create assessment: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML private void backToStep1() { showStep(1); }

    private void showStep(int step) {
        step1Pane.setVisible(step == 1); step1Pane.setManaged(step == 1);
        step2Pane.setVisible(step == 2); step2Pane.setManaged(step == 2);
    }

    // ── Sensor connection ────────────────────────────────────────
    @FXML
    private void checkConnection() {
        BleManager ble = BleManager.getInstance();
        if (!ble.isConnected()) ble.connect();
        checkBleStatus();
    }

    private void checkBleStatus() {
        BleManager ble = BleManager.getInstance();
        boolean connected = ble.isConnected() || ble.isSimulationMode();
        esp32Status.setText(connected
                ? (ble.isSimulationMode() ? "✅ Simulation Mode Active" : "✅ Connected")
                : "❌ Not connected – enable Simulation Mode or connect ESP32");
        esp32Status.setStyle(connected ? "-fx-text-fill: #4CAF50;" : "-fx-text-fill: #F44336;");
    }

    // ── Data collection ──────────────────────────────────────────
    @FXML
    private void startCollection() {
        if (collecting) return;
        collecting = true;
        collectedReadings.clear();
        readingCount = 0;

        BleManager ble = BleManager.getInstance();
        if (!ble.isConnected()) ble.connect();

        dataListener = this::onSensorData;
        ble.addDataListener(dataListener);

        startCollectionBtn.setDisable(true);
        stopCollectionBtn.setDisable(false);
        analyzeBtn.setDisable(true);

        int totalMs = collectionDurationSec * 1000;
        long startTime = System.currentTimeMillis();

        collectionExecutor = Executors.newSingleThreadScheduledExecutor();
        collectionExecutor.scheduleAtFixedRate(() -> {
            long elapsed = System.currentTimeMillis() - startTime;
            double progress = Math.min(1.0, (double) elapsed / totalMs);

            Platform.runLater(() -> {
                collectionProgress.setProgress(progress);
                collectionStatus.setText(String.format(
                        "Collecting… %.0f / %d sec", elapsed / 1000.0, collectionDurationSec));
                readingCountLabel.setText("Readings collected: " + readingCount);
            });

            if (elapsed >= totalMs) {
                stopCollection();
            }
        }, 200, 200, TimeUnit.MILLISECONDS);

        collectionStatus.setText("Collection started…");
    }

    @FXML
    private void stopCollection() {
        if (!collecting) return;
        collecting = false;

        if (collectionExecutor != null) collectionExecutor.shutdownNow();
        BleManager.getInstance().removeDataListener(dataListener);

        Platform.runLater(() -> {
            startCollectionBtn.setDisable(false);
            stopCollectionBtn.setDisable(true);
            analyzeBtn.setDisable(readingCount < 5);
            collectionStatus.setText("Collection complete. " + readingCount + " readings captured.");
            collectionProgress.setProgress(1.0);
        });
    }

    private void onSensorData(SensorData data) {
        collectedReadings.add(data);
        readingCount++;

        // Submit to backend in background
        Thread t = new Thread(() -> {
            try {
                Map<String, Object> reading = new LinkedHashMap<>();
                reading.put("sequenceNumber", data.getSequenceNumber());
                reading.put("accelerationX",  data.getAccelerationX());
                reading.put("accelerationY",  data.getAccelerationY());
                reading.put("accelerationZ",  data.getAccelerationZ());
                reading.put("gyroscopeX",     data.getGyroscopeX());
                reading.put("gyroscopeY",     data.getGyroscopeY());
                reading.put("gyroscopeZ",     data.getGyroscopeZ());
                reading.put("flexValue",      data.getFlexValue());
                reading.put("fsrValue",       data.getFsrValue());
                reading.put("vibrationState", data.isVibrationState());
                reading.put("mpuAvailable",   data.isMpuAvailable());
                reading.put("flexAvailable",  data.isFlexAvailable());
                reading.put("fsrAvailable",   data.isFsrAvailable());
                reading.put("vibAvailable",   data.isVibAvailable());

                ApiService.getInstance().submitSensorData(currentAssessmentId, reading);
            } catch (Exception ex) {
                // Non-critical: log and continue
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Analyze ──────────────────────────────────────────────────
    @FXML
    private void analyze() {
        if (currentAssessmentId < 0) {
            errorLabel.setText("No active assessment. Please go back and start again.");
            return;
        }

        errorLabel.setText("Analyzing… please wait.");
        analyzeBtn.setDisable(true);

        Thread t = new Thread(() -> {
            try {
                ApiService.getInstance().triggerPrediction(currentAssessmentId);
                final long aid = currentAssessmentId;
                Platform.runLater(() -> {
                    try { MainApp.showResults(aid); }
                    catch (Exception ex) { ex.printStackTrace(); }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    errorLabel.setText("Analysis failed: " + ex.getMessage());
                    analyzeBtn.setDisable(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
