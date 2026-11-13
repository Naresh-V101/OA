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
import com.smartkneeoa.frontend.controllers.ResultsController;
import com.smartkneeoa.frontend.MainApp;
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

public class ReportsController extends BaseController {

    @FXML private Label totalCount, lowCount, modCount, highCount;
    @FXML private PieChart  riskPieChart;
    @FXML private LineChart<String, Number> timelineChart;

    @FXML private TableView<JsonNode>     reportTable;
    @FXML private TableColumn<JsonNode, String> colId, colDate, colSide, colPain, colRisk;
    @FXML private TableColumn<JsonNode, Void>   colExport;

    private java.util.List<JsonNode> allAssessments = new ArrayList<>();

    @FXML
    public void initialize() {
        colId.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("id").asText()));
        colDate.setCellValueFactory(r -> {
            String raw = r.getValue().path("assessmentDate").asText("—");
            return new SimpleStringProperty(raw.replace("T", " ").substring(0, Math.min(16, raw.length())));
        });
        colSide.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("kneeSide").asText("—")));
        colPain.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("painScore").asText("—")));
        colRisk.setCellValueFactory(r -> new SimpleStringProperty(r.getValue().path("result").path("riskCategory").asText("Pending")));

        colExport.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("PDF");
            { btn.setStyle("-fx-background-color:#1E3A52;-fx-text-fill:#00BFA5;-fx-font-size:11px;-fx-border-color:#00BFA5;-fx-border-radius:6;-fx-background-radius:6;-fx-padding:5 12;-fx-cursor:hand;");
              btn.setOnAction(e -> {
                JsonNode row = getTableView().getItems().get(getIndex());
                exportIndividualPdf(row);
              });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        loadData();
    }

    private void loadData() {
        Thread t = new Thread(() -> {
            try {
                JsonNode resp = ApiService.getInstance().listAssessments(0, 1000);
                Platform.runLater(() -> {
                    allAssessments.clear();
                    if (resp.isArray()) resp.forEach(allAssessments::add);
                    populateUI();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> totalCount.setText("Error"));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void populateUI() {
        int total = allAssessments.size();
        long low  = allAssessments.stream().filter(a -> "LOW".equals(a.path("result").path("riskCategory").asText())).count();
        long mod  = allAssessments.stream().filter(a -> "MODERATE".equals(a.path("result").path("riskCategory").asText())).count();
        long high = allAssessments.stream().filter(a -> "HIGH".equals(a.path("result").path("riskCategory").asText())).count();

        totalCount.setText(String.valueOf(total));
        lowCount.setText(String.valueOf(low));
        modCount.setText(String.valueOf(mod));
        highCount.setText(String.valueOf(high));

        // Pie chart
        riskPieChart.getData().setAll(
            new PieChart.Data("Low ("   + low  + ")", low),
            new PieChart.Data("Moderate (" + mod + ")", mod),
            new PieChart.Data("High ("  + high + ")", high)
        );

        // Table
        ObservableList<JsonNode> rows = FXCollections.observableArrayList(allAssessments);
        reportTable.setItems(rows);
    }

    @FXML
    private void exportSummaryPdf() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Summary Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("OA_Summary_Report.pdf");
        File file = fc.showSaveDialog(null);
        if (file == null) return;

        Thread t = new Thread(() -> {
            try { generateSummaryPdf(file); }
            catch (Exception ex) { ex.printStackTrace(); }
        });
        t.setDaemon(true);
        t.start();
    }

    private void exportIndividualPdf(JsonNode a) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Report");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        fc.setInitialFileName("OA_Report_" + a.path("id").asText("") + ".pdf");
        File file = fc.showSaveDialog(null);
        if (file == null) return;

        Thread t = new Thread(() -> {
            try {
                // Fetch full assessment then show results screen
                JsonNode full = ApiService.getInstance().getAssessment(a.path("id").asLong());
                ResultsController.targetAssessmentId = full.path("id").asLong();
                Platform.runLater(() -> {
                    try { MainApp.showResults(full.path("id").asLong()); }
                    catch (Exception ex) { ex.printStackTrace(); }
                });
            } catch (Exception ex) { ex.printStackTrace(); }
        });
        t.setDaemon(true);
        t.start();
    }

    private void generateSummaryPdf(File file) throws Exception {
        Document doc = new Document();
        PdfWriter.getInstance(doc, new FileOutputStream(file));
        doc.open();

        Font title = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Font head  = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD);
        Font body  = new Font(Font.FontFamily.HELVETICA, 11);
        Font warn  = new Font(Font.FontFamily.HELVETICA, 10, Font.ITALIC, BaseColor.RED);

        doc.add(new Paragraph("Smart Knee OA – Assessment Summary Report", title));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("⚠️ DISCLAIMER: This report contains PRELIMINARY DEMONSTRATION RESULTS generated by a model trained on SYNTHETIC data. NOT for clinical use.", warn));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Summary", head));
        doc.add(new Paragraph("Total Assessments : " + allAssessments.size(), body));
        doc.add(new Paragraph("Low Risk          : " + lowCount.getText(), body));
        doc.add(new Paragraph("Moderate Risk     : " + modCount.getText(), body));
        doc.add(new Paragraph("High Risk         : " + highCount.getText(), body));
        doc.add(new Paragraph(" "));
        doc.add(new Paragraph("Individual Assessments", head));

        for (JsonNode a : allAssessments) {
            String raw = a.path("assessmentDate").asText("").replace("T", " ");
            doc.add(new Paragraph(
                "#" + a.path("id").asText() + "  |  " + raw.substring(0, Math.min(16, raw.length()))
                + "  |  " + a.path("kneeSide").asText()
                + "  |  Pain: " + a.path("painScore").asText()
                + "  |  Risk: " + a.path("result").path("riskCategory").asText("Pending"),
                body));
        }

        doc.close();
    }
}
