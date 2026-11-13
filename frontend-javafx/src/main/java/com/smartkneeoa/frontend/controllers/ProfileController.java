package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartkneeoa.frontend.MainApp;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.*;

public class ProfileController extends BaseController {

    @FXML private Label  displayName, displayEmail, displayRole;
    @FXML private Label  statAssessments, statLastRisk, statMember;
    @FXML private Label  profileMessage;

    @FXML private TextField         nameField, ageField, heightField, weightField;
    @FXML private ComboBox<String>  genderCombo;

    @FXML
    public void initialize() {
        genderCombo.setItems(FXCollections.observableArrayList("MALE", "FEMALE", "OTHER"));
        loadProfile();
    }

    @FXML
    public void loadProfile() {
        Thread t = new Thread(() -> {
            try {
                JsonNode profile = ApiService.getInstance().getProfile();
                JsonNode assessments = ApiService.getInstance().listAssessments(0, 1);

                Platform.runLater(() -> {
                    displayName.setText(profile.path("name").asText("—"));
                    displayEmail.setText(profile.path("email").asText("—"));
                    displayRole.setText(profile.path("role").asText("USER"));

                    nameField.setText(profile.path("name").asText(""));
                    ageField.setText(profile.path("age").asText(""));
                    heightField.setText(profile.path("heightCm").asText(""));
                    weightField.setText(profile.path("weightKg").asText(""));

                    String gender = profile.path("gender").asText(null);
                    if (gender != null) genderCombo.setValue(gender);

                    String createdAt = profile.path("createdAt").asText("—");
                    statMember.setText(createdAt.length() >= 10 ? createdAt.substring(0, 10) : createdAt);

                    // Stats from latest assessment
                    if (assessments.isArray() && assessments.size() > 0) {
                        statLastRisk.setText(
                            assessments.get(0).path("result").path("riskCategory").asText("—"));
                    }
                });

                // Count
                JsonNode allAssessments = ApiService.getInstance().listAssessments(0, 1000);
                Platform.runLater(() ->
                    statAssessments.setText(allAssessments.isArray()
                            ? String.valueOf(allAssessments.size()) : "0"));

            } catch (Exception ex) {
                Platform.runLater(() ->
                    profileMessage.setText("Error loading profile: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void saveProfile() {
        Map<String, Object> fields = new LinkedHashMap<>();

        String name = nameField.getText().trim();
        if (!name.isEmpty()) fields.put("name", name);

        String ageStr = ageField.getText().trim();
        if (!ageStr.isEmpty()) {
            try { fields.put("age", Integer.parseInt(ageStr)); }
            catch (NumberFormatException e) {
                profileMessage.setText("Invalid age value.");
                return;
            }
        }

        if (genderCombo.getValue() != null) fields.put("gender", genderCombo.getValue());

        String h = heightField.getText().trim();
        if (!h.isEmpty()) { try { fields.put("heightCm", Double.parseDouble(h)); } catch (Exception ignored) {} }

        String w = weightField.getText().trim();
        if (!w.isEmpty()) { try { fields.put("weightKg", Double.parseDouble(w)); } catch (Exception ignored) {} }

        Thread t = new Thread(() -> {
            try {
                ApiService.getInstance().updateProfile(fields);
                if (fields.containsKey("name")) {
                    MainApp.setCurrentUserName(fields.get("name").toString());
                }
                Platform.runLater(() -> {
                    profileMessage.setStyle("-fx-text-fill: #4CAF50;");
                    profileMessage.setText("✅ Profile updated successfully.");
                    loadProfile();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    profileMessage.setStyle("-fx-text-fill: #F44336;");
                    profileMessage.setText("Failed: " + ex.getMessage());
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
