package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartkneeoa.frontend.MainApp;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.io.IOException;

public class LoginController {

    @FXML private VBox      loginForm, registerForm;
    @FXML private Button    tabLoginBtn, tabRegisterBtn;

    // Login fields
    @FXML private TextField     loginEmail;
    @FXML private PasswordField loginPassword;
    @FXML private Label         loginError;

    // Register fields
    @FXML private TextField     regName, regEmail, regAge;
    @FXML private PasswordField regPassword;
    @FXML private ComboBox<String> regGender;
    @FXML private Label         regError;

    @FXML
    public void initialize() {
        regGender.setItems(FXCollections.observableArrayList("MALE", "FEMALE", "OTHER"));
    }

    // ── Tab switching ────────────────────────────────────────────
    @FXML
    private void switchToLogin() {
        loginForm.setVisible(true);   loginForm.setManaged(true);
        registerForm.setVisible(false); registerForm.setManaged(false);
        tabLoginBtn.getStyleClass().setAll("btn-primary");
        tabRegisterBtn.getStyleClass().setAll("btn-secondary");
    }

    @FXML
    private void switchToRegister() {
        registerForm.setVisible(true);  registerForm.setManaged(true);
        loginForm.setVisible(false);    loginForm.setManaged(false);
        tabRegisterBtn.getStyleClass().setAll("btn-primary");
        tabLoginBtn.getStyleClass().setAll("btn-secondary");
    }

    // ── Login ────────────────────────────────────────────────────
    @FXML
    private void login() {
        String email    = loginEmail.getText().trim();
        String password = loginPassword.getText();

        if (email.isEmpty() || password.isEmpty()) {
            loginError.setText("Please enter your email and password.");
            return;
        }

        loginError.setText("Signing in…");

        // Run network call off the JavaFX thread
        Thread t = new Thread(() -> {
            try {
                JsonNode resp = ApiService.getInstance().login(email, password);
                String token = resp.get("token").asText();
                long   uid   = resp.get("userId").asLong();
                String name  = resp.get("name").asText();

                MainApp.setAuthToken(token);
                MainApp.setCurrentUserId(uid);
                MainApp.setCurrentUserName(name);

                Platform.runLater(() -> {
                    try { MainApp.showDashboard(); }
                    catch (IOException ex) { ex.printStackTrace(); }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                    loginError.setText("Login failed: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    // ── Register ─────────────────────────────────────────────────
    @FXML
    private void register() {
        String name     = regName.getText().trim();
        String email    = regEmail.getText().trim();
        String password = regPassword.getText();
        String ageStr   = regAge.getText().trim();
        String gender   = regGender.getValue();

        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            regError.setText("Name, email, and password are required.");
            return;
        }
        if (password.length() < 8) {
            regError.setText("Password must be at least 8 characters.");
            return;
        }

        Integer age = null;
        if (!ageStr.isEmpty()) {
            try { age = Integer.parseInt(ageStr); }
            catch (NumberFormatException e) {
                regError.setText("Age must be a number.");
                return;
            }
        }

        regError.setText("Creating account…");
        final Integer finalAge = age;

        Thread t = new Thread(() -> {
            try {
                JsonNode resp = ApiService.getInstance()
                        .register(name, email, password, finalAge, gender, null, null);

                String token = resp.get("token").asText();
                long   uid   = resp.get("userId").asLong();
                String uname = resp.get("name").asText();

                MainApp.setAuthToken(token);
                MainApp.setCurrentUserId(uid);
                MainApp.setCurrentUserName(uname);

                Platform.runLater(() -> {
                    try { MainApp.showDashboard(); }
                    catch (IOException ex) { ex.printStackTrace(); }
                });
            } catch (Exception ex) {
                Platform.runLater(() ->
                    regError.setText("Registration failed: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
