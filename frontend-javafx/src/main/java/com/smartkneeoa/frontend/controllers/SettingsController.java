package com.smartkneeoa.frontend.controllers;

import com.smartkneeoa.frontend.ble.BleManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.*;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class SettingsController extends BaseController {

    @FXML private TextField apiUrlField;
    @FXML private TextField mlUrlField;
    @FXML private Label     apiStatus, mlStatus, mlModelVersion, settingsMessage;

    @FXML private CheckBox simModeCheck;
    @FXML private Spinner<Integer> durationSpinner;
    @FXML private TextField reportDirField;

    private static final String PREFS_FILE =
            System.getProperty("user.home") + "/.smart_knee_oa_settings.properties";

    @FXML
    public void initialize() {
        loadSettings();
        simModeCheck.setSelected(BleManager.getInstance().isSimulationMode());
        simModeCheck.selectedProperty().addListener((obs, old, val) ->
            BleManager.getInstance().setSimulationMode(val));
    }

    @FXML
    private void testApiConnection() {
        apiStatus.setText("Testing…");
        apiStatus.setStyle("-fx-text-fill: #FF9800;");
        String url = apiUrlField.getText().trim();

        Thread t = new Thread(() -> {
            try (Response r = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .build()
                        .newCall(new Request.Builder().url(url + "/actuator/health").get().build())
                        .execute()) {
                boolean ok = r.isSuccessful();
                Platform.runLater(() -> {
                    apiStatus.setText(ok ? "✅ Connected" : "❌ HTTP " + r.code());
                    apiStatus.setStyle(ok ? "-fx-text-fill:#4CAF50;" : "-fx-text-fill:#F44336;");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    apiStatus.setText("❌ " + ex.getMessage());
                    apiStatus.setStyle("-fx-text-fill:#F44336;");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void testMlConnection() {
        mlStatus.setText("Testing…");
        mlStatus.setStyle("-fx-text-fill: #FF9800;");
        String url = mlUrlField.getText().trim();

        Thread t = new Thread(() -> {
            try (Response r = new OkHttpClient.Builder()
                        .connectTimeout(5, TimeUnit.SECONDS)
                        .build()
                        .newCall(new Request.Builder().url(url + "/health").get().build())
                        .execute()) {
                boolean ok = r.isSuccessful();
                String body = r.body() != null ? r.body().string() : "";
                Platform.runLater(() -> {
                    mlStatus.setText(ok ? "✅ Connected" : "❌ HTTP " + r.code());
                    mlStatus.setStyle(ok ? "-fx-text-fill:#4CAF50;" : "-fx-text-fill:#F44336;");
                    if (body.contains("model_version")) {
                        // extract version
                        int idx = body.indexOf("model_version") + 16;
                        String ver = body.substring(idx, Math.min(idx + 20, body.length())).replaceAll("[^\\w.\\-]", "");
                        mlModelVersion.setText("Model: " + ver);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    mlStatus.setText("❌ " + ex.getMessage());
                    mlStatus.setStyle("-fx-text-fill:#F44336;");
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void browseReportDir() {
        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("Select Report Output Directory");
        File dir = dc.showDialog(null);
        if (dir != null) reportDirField.setText(dir.getAbsolutePath());
    }

    @FXML
    private void saveSettings() {
        try {
            Properties props = new Properties();
            props.setProperty("api.url",          apiUrlField.getText().trim());
            props.setProperty("ml.url",           mlUrlField.getText().trim());
            props.setProperty("ble.simulation",   String.valueOf(simModeCheck.isSelected()));
            props.setProperty("collection.sec",   String.valueOf(durationSpinner.getValue()));
            props.setProperty("reports.dir",      reportDirField.getText().trim());

            try (FileOutputStream fos = new FileOutputStream(PREFS_FILE)) {
                props.store(fos, "Smart Knee OA Settings");
            }

            BleManager.getInstance().setSimulationMode(simModeCheck.isSelected());

            settingsMessage.setStyle("-fx-text-fill:#4CAF50;");
            settingsMessage.setText("✅ Settings saved.");
        } catch (Exception ex) {
            settingsMessage.setStyle("-fx-text-fill:#F44336;");
            settingsMessage.setText("Failed: " + ex.getMessage());
        }
    }

    @FXML
    private void resetDefaults() {
        apiUrlField.setText("http://localhost:8080");
        mlUrlField.setText("http://localhost:8000");
        simModeCheck.setSelected(true);
        durationSpinner.getValueFactory().setValue(30);
        reportDirField.setText("./reports");
        settingsMessage.setText("Defaults restored.");
    }

    private void loadSettings() {
        try {
            Properties props = new Properties();
            File f = new File(PREFS_FILE);
            if (f.exists()) {
                props.load(new FileInputStream(f));
            }
            apiUrlField.setText(props.getProperty("api.url",   "http://localhost:8080"));
            mlUrlField.setText( props.getProperty("ml.url",    "http://localhost:8000"));
            simModeCheck.setSelected(Boolean.parseBoolean(props.getProperty("ble.simulation", "true")));
            reportDirField.setText(props.getProperty("reports.dir", "./reports"));
            int dur = Integer.parseInt(props.getProperty("collection.sec", "30"));
            durationSpinner.getValueFactory().setValue(dur);
        } catch (Exception ignored) {
            apiUrlField.setText("http://localhost:8080");
            mlUrlField.setText("http://localhost:8000");
        }
    }
}
