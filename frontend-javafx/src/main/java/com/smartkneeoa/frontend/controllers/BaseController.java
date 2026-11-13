package com.smartkneeoa.frontend.controllers;

import com.smartkneeoa.frontend.MainApp;
import javafx.fxml.FXML;

import java.io.IOException;

/**
 * Base controller providing shared navigation handlers
 * to every screen that has a sidebar.
 */
public abstract class BaseController {

    @FXML public void navDashboard()     throws IOException { MainApp.showDashboard(); }
    @FXML public void navNewAssessment() throws IOException { MainApp.showNewAssessment(); }
    @FXML public void navLiveMonitor()   throws IOException { MainApp.showLiveMonitor(); }
    @FXML public void navHistory()       throws IOException { MainApp.showHistory(); }
    @FXML public void navReports()       throws IOException { MainApp.showReports(); }
    @FXML public void navProfile()       throws IOException { MainApp.showProfile(); }
    @FXML public void navSettings()      throws IOException { MainApp.showSettings(); }
    @FXML public void logout()                              { MainApp.logout(); }
}
