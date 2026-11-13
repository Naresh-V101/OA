package com.smartkneeoa.frontend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

/**
 * Application entry point.
 * Opens the Login screen. After successful authentication, the main
 * dashboard shell is loaded.
 */
public class MainApp extends Application {

    // ── Application-wide state ──────────────────────────────────
    private static Stage    primaryStage;
    private static String   authToken;
    private static Long     currentUserId;
    private static String   currentUserName;

    // ── API base URL (override with -Dapi.url=... JVM arg) ──────
    public static final String API_BASE_URL =
            System.getProperty("api.url", "http://localhost:8080");

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        stage.setTitle("Smart Knee OA Assessment System");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);

        // Try to set app icon (optional – file may not exist in early dev)
        try {
            stage.getIcons().add(
                new Image(Objects.requireNonNull(
                    getClass().getResourceAsStream("/images/icon.png"))));
        } catch (Exception ignored) {}

        showLoginScreen();
        stage.show();
    }

    // ── Screen navigation helpers ────────────────────────────────

    public static void showLoginScreen() throws IOException {
        loadScene("/fxml/Login.fxml", 480, 600, false);
        primaryStage.setResizable(false);
    }

    public static void showDashboard() throws IOException {
        loadScene("/fxml/Dashboard.fxml", 1200, 750, true);
        primaryStage.setResizable(true);
    }

    public static void showNewAssessment() throws IOException {
        loadScene("/fxml/NewAssessment.fxml", 1200, 750, true);
    }

    public static void showLiveMonitor() throws IOException {
        loadScene("/fxml/LiveMonitor.fxml", 1200, 750, true);
    }

    public static void showResults(Long assessmentId) throws IOException {
        loadScene("/fxml/Results.fxml?aid=" + assessmentId, 1200, 750, true);
    }

    public static void showHistory() throws IOException {
        loadScene("/fxml/History.fxml", 1200, 750, true);
    }

    public static void showProfile() throws IOException {
        loadScene("/fxml/Profile.fxml", 1200, 750, true);
    }

    public static void showReports() throws IOException {
        loadScene("/fxml/Reports.fxml", 1200, 750, true);
    }

    public static void showSettings() throws IOException {
        loadScene("/fxml/Settings.fxml", 1200, 750, true);
    }

    private static void loadScene(String fxmlPath, double width, double height,
                                   boolean withSidebar) throws IOException {
        // Strip query params for FXMLLoader
        String cleanPath = fxmlPath.contains("?") ? fxmlPath.substring(0, fxmlPath.indexOf('?')) : fxmlPath;
        FXMLLoader loader = new FXMLLoader(MainApp.class.getResource(cleanPath));
        Scene scene = new Scene(loader.load(), width, height);
        scene.getStylesheets().add(
            Objects.requireNonNull(MainApp.class.getResource("/css/styles.css")).toExternalForm());
        primaryStage.setScene(scene);
    }

    // ── Static accessors ─────────────────────────────────────────

    public static Stage getPrimaryStage()       { return primaryStage; }
    public static String getAuthToken()          { return authToken; }
    public static Long   getCurrentUserId()      { return currentUserId; }
    public static String getCurrentUserName()    { return currentUserName; }

    public static void setAuthToken(String token)       { authToken = token; }
    public static void setCurrentUserId(Long id)        { currentUserId = id; }
    public static void setCurrentUserName(String name)  { currentUserName = name; }

    public static void logout() {
        authToken       = null;
        currentUserId   = null;
        currentUserName = null;
        try { showLoginScreen(); } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
