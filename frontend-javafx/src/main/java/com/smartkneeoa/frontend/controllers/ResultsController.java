package com.smartkneeoa.frontend.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.itextpdf.text.Document;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Element;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.smartkneeoa.frontend.services.ApiService;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.*;

public class ResultsController extends BaseController {

    // Injected by FXML URL parameter via MainApp.showResults(id)
    // We use a static field as a simple pass-through for the current assessment id
    public static long targetAssessmentId = -1;

    @FXML private Label riskCategoryLabel, riskBadge, riskGuidance;
    @FXML private Label modelVersionLabel, demoBadge;
    @FXML private Label detailId, detailDate, detailSide, detailPain, detailReadings;
    @FXML private BarChart<String, Number> probabilityChart;

    @FXML private TableView<String[]>     featureTable;
    @FXML private TableColumn<String[], String> featureNameCol, featureValueCol;

    private JsonNode currentAssessment;

    @FXML
    public void initialize() {
        featureNameCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue()[0]));
        featureValueCol.setCellValueFactory(r -> new SimpleStringProperty(r.getValue()[1]));

        if (targetAssessmentId > 0) {
            loadResult(targetAssessmentId);
        }
    }

    private void loadResult(long assessmentId) {
        Thread t = new Thread(() -> {
            try {
                JsonNode a = ApiService.getInstance().getAssessment(assessmentId);
                Platform.runLater(() -> populateUI(a));
            } catch (Exception ex) {
                Platform.runLater(() ->
                    riskCategoryLabel.setText("Error: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void populateUI(JsonNode a) {
        currentAssessment = a;

        // Details
        detailId.setText("#" + a.path("id").asText("—"));
        String rawDate = a.path("assessmentDate").asText("—");
        detailDate.setText(rawDate.replace("T", " ").substring(0, Math.min(16, rawDate.length())));
        detailSide.setText(a.path("kneeSide").asText("—"));
        detailPain.setText(a.path("painScore").asText("—") + " / 10");

        // Result
        JsonNode result = a.path("result");
        if (!result.isMissingNode()) {
            String risk  = result.path("riskCategory").asText("—");
            riskCategoryLabel.setText(risk);
            riskCategoryLabel.setStyle(riskStyle(risk) + " -fx-font-size: 42px; -fx-font-weight: 800;");

            riskBadge.setText(risk + " RISK");
            riskBadge.getStyleClass().setAll("badge-" + risk.toLowerCase());
            riskGuidance.setText(guidance(risk));

            modelVersionLabel.setText("Model: " + result.path("modelVersion").asText("—"));
            if (result.path("isDemo").asBoolean(true)) {
                demoBadge.setText("⚠️ DEMO – Synthetic Data – Not Clinically Validated");
            }

            // Probability bar chart
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Probability");
            series.getData().add(new XYChart.Data<>("LOW",      result.path("probLow").asDouble(0)));
            series.getData().add(new XYChart.Data<>("MODERATE", result.path("probModerate").asDouble(0)));
            series.getData().add(new XYChart.Data<>("HIGH",     result.path("probHigh").asDouble(0)));
            probabilityChart.getData().setAll(series);

            // Feature table (feature summary from result)
            ObservableList<String[]> featureRows = FXCollections.observableArrayList();
            featureRows.add(new String[]{"Prediction Score",   String.format("%.4f", result.path("predictionScore").asDouble())});
            featureRows.add(new String[]{"Prob – LOW",         String.format("%.2f%%", result.path("probLow").asDouble() * 100)});
            featureRows.add(new String[]{"Prob – MODERATE",    String.format("%.2f%%", result.path("probModerate").asDouble() * 100)});
            featureRows.add(new String[]{"Prob – HIGH",        String.format("%.2f%%", result.path("probHigh").asDouble() * 100)});
            featureRows.add(new String[]{"Pain Score",         a.path("painScore").asText("N/A")});
            featureRows.add(new String[]{"Knee Side",          a.path("kneeSide").asText("—")});
            featureTable.setItems(featureRows);
        }
    }

    @FXML
    private void exportPdf() {
        if (currentAssessment == null) return;

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Assessment Report");
        fc.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        fc.setInitialFileName("OA_Report_" + currentAssessment.path("id").asText("") + ".pdf");

        File file = fc.showSaveDialog(null);
        if (file == null) return;

        Thread t = new Thread(() -> {
            try {
                generatePdf(file, currentAssessment);
                Platform.runLater(() ->
                    showAlert("PDF Exported", "Report saved to:\n" + file.getAbsolutePath()));
            } catch (Exception ex) {
                Platform.runLater(() ->
                    showAlert("Export Failed", ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void generatePdf(File file, JsonNode a) throws Exception {
        Document doc = new Document();
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();

        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Font headFont  = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD);
        Font bodyFont  = new Font(Font.FontFamily.HELVETICA, 11);
        Font warnFont  = new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC, BaseColor.RED);

        doc.add(new Paragraph("Smart Knee OA Assessment Report", titleFont));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("⚠️ DISCLAIMER: This report contains a PRELIMINARY DEMONSTRATION RESULT "
                + "generated by a model trained on SYNTHETIC data. It is NOT clinically validated "
                + "and must NOT be used for medical diagnosis.", warnFont));
        doc.add(new Paragraph(" "));

        doc.add(new Paragraph("Assessment Details", headFont));
        doc.add(new Paragraph("ID       : " + a.path("id").asText(), bodyFont));
        doc.add(new Paragraph("Date     : " + a.path("assessmentDate").asText(), bodyFont));
        doc.add(new Paragraph("Knee Side: " + a.path("kneeSide").asText(), bodyFont));
        doc.add(new Paragraph("Pain     : " + a.path("painScore").asText() + " / 10", bodyFont));
        doc.add(new Paragraph(" "));

        JsonNode result = a.path("result");
        if (!result.isMissingNode()) {
            doc.add(new Paragraph("Risk Assessment", headFont));
            doc.add(new Paragraph("Risk Category : " + result.path("riskCategory").asText(), headFont));
            doc.add(new Paragraph("Prediction Score: " + String.format("%.4f", result.path("predictionScore").asDouble(0)), bodyFont));
            doc.add(new Paragraph("Prob – LOW      : " + String.format("%.2f%%", result.path("probLow").asDouble(0) * 100), bodyFont));
            doc.add(new Paragraph("Prob – MODERATE : " + String.format("%.2f%%", result.path("probModerate").asDouble(0) * 100), bodyFont));
            doc.add(new Paragraph("Prob – HIGH     : " + String.format("%.2f%%", result.path("probHigh").asDouble(0) * 100), bodyFont));
            doc.add(new Paragraph("Model Version   : " + result.path("modelVersion").asText(), bodyFont));
            doc.add(new Paragraph("Demo Result     : " + result.path("isDemo").asBoolean(true), bodyFont));
        }

        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("General Guidance", headFont));
        doc.add(new Paragraph(guidance(result.path("riskCategory").asText("—")), bodyFont));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Always consult a qualified healthcare professional for proper evaluation.", warnFont));

        doc.close();
    }

    private String riskStyle(String risk) {
        return switch (risk.toUpperCase()) {
            case "LOW"      -> "-fx-text-fill: #4CAF50;";
            case "MODERATE" -> "-fx-text-fill: #FF9800;";
            case "HIGH"     -> "-fx-text-fill: #F44336;";
            default         -> "-fx-text-fill: #8BAFC9;";
        };
    }

    private String guidance(String risk) {
        return switch (risk.toUpperCase()) {
            case "LOW"      -> "Preliminary indicators suggest low risk. Maintain an active lifestyle, "
                             + "healthy weight, and regular exercise to support joint health.";
            case "MODERATE" -> "Preliminary indicators suggest moderate risk. Consider discussing your "
                             + "knee symptoms with a healthcare professional. Light exercise and weight "
                             + "management may be beneficial.";
            case "HIGH"     -> "Preliminary indicators suggest high risk. It is strongly recommended to "
                             + "consult a qualified orthopaedic specialist for a thorough evaluation.";
            default         -> "";
        };
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.setTitle(title);
        a.showAndWait();
    }
}
