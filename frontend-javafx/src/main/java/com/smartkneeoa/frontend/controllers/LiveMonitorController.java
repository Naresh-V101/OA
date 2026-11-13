package com.smartkneeoa.frontend.controllers;

import com.smartkneeoa.frontend.ble.BleManager;
import com.smartkneeoa.frontend.models.SensorData;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.function.Consumer;

public class LiveMonitorController extends BaseController {

    // BLE controls
    @FXML private Button   connectBtn;
    @FXML private CheckBox simModeCheck;
    @FXML private Circle   bleStatusDot;
    @FXML private Label    bleStatusLabel;
    @FXML private Label    sampleCountLabel;

    // Sensor status dots & labels
    @FXML private Circle mpuDot, flexDot, fsrDot, vibDot;
    @FXML private Label  mpuStatus, flexStatus, fsrStatus, vibStatus;

    // Accelerometer
    @FXML private Label accelX, accelY, accelZ;
    @FXML private LineChart<Number, Number> accelChart;

    // Gyroscope
    @FXML private Label gyroX, gyroY, gyroZ;
    @FXML private LineChart<Number, Number> gyroChart;

    // Flex / FSR / Vibration
    @FXML private Label       flexValue, fsrValue, vibCount, vibState;
    @FXML private ProgressBar flexBar, fsrBar;

    // Chart series
    private XYChart.Series<Number, Number> axSeries, aySeries, azSeries;
    private XYChart.Series<Number, Number> gxSeries, gySeries, gzSeries;

    private static final int MAX_POINTS   = 80;
    private int sampleCount   = 0;
    private int vibEventCount = 0;

    private Consumer<SensorData>            dataListener;
    private Consumer<BleManager.ConnectionState> stateListener;

    @FXML
    public void initialize() {
        // Init chart series
        axSeries = series("AX"); aySeries = series("AY"); azSeries = series("AZ");
        gxSeries = series("GX"); gySeries = series("GY"); gzSeries = series("GZ");

        accelChart.getData().addAll(axSeries, aySeries, azSeries);
        gyroChart.getData().addAll(gxSeries, gySeries, gzSeries);

        accelChart.setCreateSymbols(false);
        gyroChart.setCreateSymbols(false);

        // Wire listeners
        dataListener  = this::onSensorData;
        stateListener = this::onStateChange;

        BleManager ble = BleManager.getInstance();
        ble.addDataListener(dataListener);
        ble.addStateListener(stateListener);

        simModeCheck.setSelected(ble.isSimulationMode());
        simModeCheck.selectedProperty().addListener((obs, old, val) ->
            BleManager.getInstance().setSimulationMode(val));

        // Reflect current state
        onStateChange(ble.getState());
    }

    // Called when the scene is being destroyed; unregister listeners
    public void cleanup() {
        BleManager.getInstance().removeDataListener(dataListener);
        BleManager.getInstance().removeStateListener(stateListener);
    }

    @FXML
    private void toggleConnection() {
        BleManager ble = BleManager.getInstance();
        if (ble.isConnected()) {
            ble.disconnect();
        } else {
            ble.setSimulationMode(simModeCheck.isSelected());
            ble.connect();
        }
    }

    private void onStateChange(BleManager.ConnectionState state) {
        Platform.runLater(() -> {
            switch (state) {
                case CONNECTED -> {
                    bleStatusDot.setFill(Color.web("#4CAF50"));
                    bleStatusLabel.setText("Connected" + (BleManager.getInstance().isSimulationMode() ? " (Sim)" : ""));
                    connectBtn.setText("Disconnect");
                    setAllSensorsOnline();
                }
                case SCANNING, CONNECTING -> {
                    bleStatusDot.setFill(Color.web("#FF9800"));
                    bleStatusLabel.setText("Scanning…");
                    connectBtn.setText("Scanning…");
                }
                case DISCONNECTED -> {
                    bleStatusDot.setFill(Color.web("#F44336"));
                    bleStatusLabel.setText("Disconnected");
                    connectBtn.setText("Connect Device");
                    setAllSensorsOffline();
                }
            }
        });
    }

    private void onSensorData(SensorData d) {
        sampleCount++;

        // Update sensor status
        updateSensorStatus(mpuDot,  mpuStatus,  d.isMpuAvailable(),  "MPU6050");
        updateSensorStatus(flexDot, flexStatus, d.isFlexAvailable(), "Flex Sensor");
        updateSensorStatus(fsrDot,  fsrStatus,  d.isFsrAvailable(),  "FSR Sensor");
        updateSensorStatus(vibDot,  vibStatus,  d.isVibAvailable(),  "Vibration");

        // Labels
        accelX.setText(String.format("%.4f", d.getAccelerationX()));
        accelY.setText(String.format("%.4f", d.getAccelerationY()));
        accelZ.setText(String.format("%.4f", d.getAccelerationZ()));
        gyroX.setText(String.format("%.4f", d.getGyroscopeX()));
        gyroY.setText(String.format("%.4f", d.getGyroscopeY()));
        gyroZ.setText(String.format("%.4f", d.getGyroscopeZ()));

        flexValue.setText(String.valueOf(d.getFlexValue()));
        fsrValue.setText(String.valueOf(d.getFsrValue()));
        flexBar.setProgress(d.getFlexValue() / 4095.0);
        fsrBar.setProgress(d.getFsrValue() / 4095.0);

        if (d.isVibrationState()) vibEventCount++;
        vibCount.setText(String.valueOf(vibEventCount));
        vibState.setText("State: " + (d.isVibrationState() ? "⚡ Event detected!" : "No event"));

        // Charts
        addPoint(axSeries, sampleCount, d.getAccelerationX());
        addPoint(aySeries, sampleCount, d.getAccelerationY());
        addPoint(azSeries, sampleCount, d.getAccelerationZ());
        addPoint(gxSeries, sampleCount, d.getGyroscopeX());
        addPoint(gySeries, sampleCount, d.getGyroscopeY());
        addPoint(gzSeries, sampleCount, d.getGyroscopeZ());

        sampleCountLabel.setText("Samples: " + sampleCount);
    }

    private void addPoint(XYChart.Series<Number, Number> series, int x, double y) {
        series.getData().add(new XYChart.Data<>(x, y));
        if (series.getData().size() > MAX_POINTS) {
            series.getData().remove(0);
        }
    }

    private void updateSensorStatus(Circle dot, Label label, boolean available, String name) {
        dot.setFill(available ? Color.web("#4CAF50") : Color.web("#F44336"));
        label.setText(available ? "Online" : "Unavailable");
    }

    private void setAllSensorsOnline() {
        for (Circle dot : new Circle[]{mpuDot, flexDot, fsrDot, vibDot})
            dot.setFill(Color.web("#4CAF50"));
        for (Label l : new Label[]{mpuStatus, flexStatus, fsrStatus, vibStatus})
            l.setText("Online");
    }

    private void setAllSensorsOffline() {
        for (Circle dot : new Circle[]{mpuDot, flexDot, fsrDot, vibDot})
            dot.setFill(Color.web("#F44336"));
        for (Label l : new Label[]{mpuStatus, flexStatus, fsrStatus, vibStatus})
            l.setText("Offline");
    }

    private XYChart.Series<Number, Number> series(String name) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        s.setData(FXCollections.observableArrayList());
        return s;
    }
}
