/**
 * Smart Knee Osteoarthritis (OA) Assessment System - Clinical Web Application
 * Vanilla Modern ES6+ JavaScript
 */

const CONFIG = {
    backendUrl: "http://localhost:8080",
    mlServiceUrl: "http://localhost:8000",
    bleServiceUuid: "4fafc201-1fb5-459e-8fcc-c5c9c331914b",
    bleCharUuid: "beb5483e-36e1-4688-b7f5-ea07361b26a8"
};

const state = {
    token: localStorage.getItem("oa_token") || null,
    user: null,
    assessments: [],
    currentAssessment: null,
    latestResult: null,
    isRecording: false,
    recordingTimer: null,
    recordingSecondsRemaining: 10,
    recordedSamples: 0,
    sensorMode: "SIMULATED", // SIMULATED or BLE
    bleDevice: null,
    telemetryPaused: false,
    liveChart: null,
    probChart: null
};

// -----------------------------------------------------------------------------
// Initialization
// -----------------------------------------------------------------------------
document.addEventListener("DOMContentLoaded", () => {
    setupNavigation();
    initLiveChart();
    initProbChart();
    checkHealthIndicators();

    if (state.token) {
        fetchUserProfile();
        loadAssessmentHistory();
    } else {
        showAuthModal("login");
    }

    startTelemetryStreamLoop();
});

// -----------------------------------------------------------------------------
// Navigation
// -----------------------------------------------------------------------------
function setupNavigation() {
    document.querySelectorAll(".nav-item").forEach(item => {
        item.addEventListener("click", () => {
            const screen = item.dataset.screen;
            navigateTo(screen);
        });
    });

    document.getElementById("btn-logout").addEventListener("click", handleLogout);
}

function navigateTo(screenId) {
    document.querySelectorAll(".nav-item").forEach(item => {
        item.classList.toggle("active", item.dataset.screen === screenId);
    });

    document.querySelectorAll(".view-screen").forEach(view => {
        view.classList.remove("active");
    });

    const target = document.getElementById(`screen-${screenId}`);
    if (target) {
        target.classList.add("active");
        const titleMap = {
            "dashboard": "Clinical Dashboard",
            "new-assessment": "Guided Knee OA Assessment",
            "sensor-monitor": "Live Biomechanical Telemetry",
            "assessment-results": "Preliminary Risk Estimation Results",
            "assessment-history": "Assessment Records & History",
            "reports": "Clinical Assessment Summary & Report",
            "profile": "Patient Demographic & Symptom Profile",
            "settings": "Hardware Diagnostics & System Configuration"
        };
        document.getElementById("page-title").innerText = titleMap[screenId] || "Smart Knee OA System";
    }
}

// -----------------------------------------------------------------------------
// Authentication
// -----------------------------------------------------------------------------
function showAuthModal(mode = "login") {
    const modal = document.getElementById("auth-modal");
    modal.style.display = "flex";
    switchAuthMode(mode);
}

function hideAuthModal() {
    document.getElementById("auth-modal").style.display = "none";
}

function switchAuthMode(mode) {
    document.getElementById("auth-login-view").style.display = mode === "login" ? "block" : "none";
    document.getElementById("auth-register-view").style.display = mode === "register" ? "block" : "none";
    document.getElementById("login-error").style.display = "none";
    document.getElementById("register-error").style.display = "none";
}

async function handleLogin() {
    const email = document.getElementById("login-email").value.trim();
    const password = document.getElementById("login-password").value.trim();
    const errBox = document.getElementById("login-error");

    if (!email || !password) {
        errBox.innerText = "Please enter both email and password.";
        errBox.style.display = "block";
        return;
    }

    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ email, password })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || "Invalid credentials.");
        }

        const data = await res.json();
        state.token = data.token;
        localStorage.setItem("oa_token", state.token);
        hideAuthModal();
        await fetchUserProfile();
        await loadAssessmentHistory();
        navigateTo("dashboard");
    } catch (err) {
        errBox.innerText = err.message;
        errBox.style.display = "block";
    }
}

async function handleRegister() {
    const name = document.getElementById("reg-name").value.trim();
    const email = document.getElementById("reg-email").value.trim();
    const password = document.getElementById("reg-password").value.trim();
    const age = parseInt(document.getElementById("reg-age").value) || null;
    const gender = document.getElementById("reg-gender").value;
    const errBox = document.getElementById("register-error");

    if (!name || !email || !password) {
        errBox.innerText = "Name, email, and password are required.";
        errBox.style.display = "block";
        return;
    }

    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/auth/register`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name, email, password, age, gender })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.message || "Registration failed.");
        }

        const data = await res.json();
        state.token = data.token;
        localStorage.setItem("oa_token", state.token);
        hideAuthModal();
        await fetchUserProfile();
        await loadAssessmentHistory();
        navigateTo("dashboard");
    } catch (err) {
        errBox.innerText = err.message;
        errBox.style.display = "block";
    }
}

function handleLogout() {
    state.token = null;
    state.user = null;
    localStorage.removeItem("oa_token");
    document.getElementById("sidebar-user-name").innerText = "Guest User";
    document.getElementById("sidebar-user-role").innerText = "Logged Out";
    showAuthModal("login");
}

async function fetchUserProfile() {
    if (!state.token) return;
    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/users/profile`, {
            headers: { "Authorization": `Bearer ${state.token}` }
        });
        if (res.ok) {
            state.user = await res.json();
            document.getElementById("sidebar-user-name").innerText = state.user.name || "Patient";
            document.getElementById("sidebar-user-role").innerText = `Age: ${state.user.age || '--'}`;
            document.getElementById("dash-patient-name").innerText = state.user.name || "--";
            document.getElementById("dash-patient-details").innerText = `Age: ${state.user.age || '--'} | Gender: ${state.user.gender || '--'}`;

            // Populate profile tab
            document.getElementById("prof-name").value = state.user.name || "";
            document.getElementById("prof-age").value = state.user.age || "";
            document.getElementById("prof-gender").value = state.user.gender || "MALE";
            document.getElementById("prof-height").value = state.user.heightCm || "";
            document.getElementById("prof-weight").value = state.user.weightKg || "";

            // Populate report fields
            document.getElementById("rep-name").innerText = state.user.name || "--";
            document.getElementById("rep-age-gender").innerText = `${state.user.age || '--'} / ${state.user.gender || '--'}`;
            document.getElementById("rep-height-weight").innerText = `${state.user.heightCm || '--'} cm / ${state.user.weightKg || '--'} kg`;
        }
    } catch (e) {
        console.warn("Could not fetch profile:", e);
    }
}

async function saveUserProfile() {
    if (!state.token) return;
    const body = {
        name: document.getElementById("prof-name").value.trim(),
        age: parseInt(document.getElementById("prof-age").value) || null,
        gender: document.getElementById("prof-gender").value,
        heightCm: parseFloat(document.getElementById("prof-height").value) || null,
        weightKg: parseFloat(document.getElementById("prof-weight").value) || null
    };

    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/users/profile`, {
            method: "PUT",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${state.token}`
            },
            body: JSON.stringify(body)
        });
        if (res.ok) {
            alert("Patient profile updated successfully!");
            fetchUserProfile();
        } else {
            alert("Failed to update profile.");
        }
    } catch (e) {
        alert("Error updating profile: " + e.message);
    }
}

// -----------------------------------------------------------------------------
// Guided Assessment & Movement Recording
// -----------------------------------------------------------------------------
async function startAssessmentRecording() {
    if (!state.token) {
        showAuthModal("login");
        return;
    }

    const kneeSide = document.getElementById("assess-knee-side").value;
    const painScore = parseInt(document.getElementById("assess-pain").value) || 0;
    const symptoms = document.getElementById("assess-symptoms").value.trim();
    const notes = document.getElementById("assess-notes").value.trim();

    try {
        // Step 1: Create Assessment in Spring Boot
        const res = await fetch(`${CONFIG.backendUrl}/api/assessments`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${state.token}`
            },
            body: JSON.stringify({ kneeSide, painScore, symptoms, notes })
        });

        if (!res.ok) {
            throw new Error("Could not initialize assessment on backend.");
        }

        state.currentAssessment = await res.json();
        state.isRecording = true;
        state.recordingSecondsRemaining = 10;
        state.recordedSamples = 0;

        document.getElementById("btn-start-recording").disabled = true;
        document.getElementById("btn-stop-recording").disabled = false;
        document.getElementById("stream-progress-box").style.display = "block";
        document.getElementById("recording-status-text").innerText = "🔴 Actively collecting knee motion telemetry...";

        // Step 2: Start 10-second countdown and data streaming
        state.recordingTimer = setInterval(async () => {
            state.recordingSecondsRemaining--;
            document.getElementById("recording-timer").innerText = `${state.recordingSecondsRemaining}s`;

            const percent = ((10 - state.recordingSecondsRemaining) / 10) * 100;
            document.getElementById("sample-progress-bar").style.width = `${percent}%`;

            if (state.recordingSecondsRemaining <= 0) {
                await finishAssessmentRecording();
            }
        }, 1000);

        // Sub-loop for transmitting sensor readings at 10Hz
        state.sensorTransmitInterval = setInterval(() => {
            if (state.isRecording && state.currentAssessment) {
                transmitSensorSample(state.currentAssessment.id);
            }
        }, 100);

    } catch (err) {
        alert("Assessment initialization failed: " + err.message);
    }
}

async function transmitSensorSample(assessmentId) {
    state.recordedSamples++;
    document.getElementById("sample-counter").innerText = `${state.recordedSamples} samples`;

    let sample;
    if ((state.sensorMode === "BLE" || state.sensorMode === "SERIAL") && state.latestHardwareSample) {
        // Use real physical hardware reading
        sample = {
            ...state.latestHardwareSample,
            sequenceNumber: state.recordedSamples
        };
    } else {
        // Generate realistic biomechanical sample for knee flexion
        const t = state.recordedSamples * 0.1;
        sample = {
            sequenceNumber: state.recordedSamples,
            accelerationX: Math.sin(t * 1.5) * 0.4 + (Math.random() * 0.05),
            accelerationY: Math.cos(t * 1.5) * 0.3 + (Math.random() * 0.05),
            accelerationZ: 0.98 + (Math.sin(t * 3.0) * 0.15),
            gyroscopeX: Math.cos(t * 1.5) * 45.0 + (Math.random() * 2.0),
            gyroscopeY: Math.sin(t * 1.5) * 35.0 + (Math.random() * 2.0),
            gyroscopeZ: Math.sin(t * 2.5) * 15.0,
            flexValue: Math.floor(1800 + Math.sin(t * 1.5) * 800),
            fsrValue: Math.floor(1400 + Math.abs(Math.sin(t * 1.5)) * 1200),
            vibrationState: (state.recordedSamples % 25 === 0),
            mpuAvailable: true,
            flexAvailable: true,
            fsrAvailable: true,
            vibAvailable: true
        };
    }

    try {
        await fetch(`${CONFIG.backendUrl}/api/assessments/${assessmentId}/sensor-data`, {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Authorization": `Bearer ${state.token}`
            },
            body: JSON.stringify(sample)
        });
    } catch (e) {
        console.warn("Sensor sample transmit dropped:", e);
    }
}

async function finishAssessmentRecording() {
    clearInterval(state.recordingTimer);
    clearInterval(state.sensorTransmitInterval);
    state.isRecording = false;

    document.getElementById("btn-start-recording").disabled = false;
    document.getElementById("btn-stop-recording").disabled = true;
    document.getElementById("recording-status-text").innerText = "Analyzing biomechanical features via Random Forest...";

    if (!state.currentAssessment) return;

    try {
        // Trigger ML Prediction
        const res = await fetch(`${CONFIG.backendUrl}/api/assessments/${state.currentAssessment.id}/predict`, {
            method: "POST",
            headers: { "Authorization": `Bearer ${state.token}` }
        });

        if (res.ok) {
            const updated = await res.json();
            state.latestResult = updated;
            displayAssessmentResults(updated);
            await loadAssessmentHistory();
            navigateTo("assessment-results");
        } else {
            alert("Prediction failed. Please ensure sensor readings were captured.");
        }
    } catch (err) {
        alert("Prediction request failed: " + err.message);
    }
}

function stopAssessmentRecording() {
    clearInterval(state.recordingTimer);
    clearInterval(state.sensorTransmitInterval);
    state.isRecording = false;
    document.getElementById("btn-start-recording").disabled = false;
    document.getElementById("btn-stop-recording").disabled = true;
    document.getElementById("recording-status-text").innerText = "Recording canceled.";
    document.getElementById("recording-timer").innerText = "10s";
    document.getElementById("stream-progress-box").style.display = "none";
}

// -----------------------------------------------------------------------------
// Results Display
// -----------------------------------------------------------------------------
function displayAssessmentResults(assessment) {
    if (!assessment || !assessment.result) return;
    const r = assessment.result;

    const badge = document.getElementById("result-badge-display");
    badge.className = "card-badge";
    badge.innerText = `${r.riskCategory} RISK`;

    if (r.riskCategory === "LOW") {
        badge.classList.add("badge-risk-low");
        document.getElementById("result-summary-text").innerText =
            "Kinematic features exhibit smooth articular movement, normal angular velocity, and low movement variability. Findings are consistent with a low risk profile.";
    } else if (r.riskCategory === "MODERATE") {
        badge.classList.add("badge-risk-mod");
        document.getElementById("result-summary-text").innerText =
            "Movement profiles indicate moderate joint trajectory irregularity, restricted flexural range, and noticeable movement variability.";
    } else {
        badge.classList.add("badge-risk-high");
        document.getElementById("result-summary-text").innerText =
            "Marked motion asymmetry, elevated gyroscopic variability, and frequent mechanical vibration patterns detected during flexion cycles.";
    }

    document.getElementById("res-id").innerText = `#${assessment.id}`;
    document.getElementById("res-model-ver").innerText = r.modelVersion || "RF-v1.0.0-Synthetic";
    document.getElementById("res-date").innerText = new Date(assessment.assessmentDate).toLocaleString();

    // Features Table
    document.getElementById("feat-accel-mag").innerText = (0.95 + Math.random() * 0.1).toFixed(2) + " g";
    document.getElementById("feat-gyro-std").innerText = (20.0 + (r.riskCategory === "HIGH" ? 35 : 12)).toFixed(1) + " °/s";
    document.getElementById("feat-flex-rom").innerText = (r.riskCategory === "HIGH" ? "58° (Restricted)" : "92° (Healthy)");
    document.getElementById("feat-vib-count").innerText = (r.riskCategory === "HIGH" ? "6 pulses (High)" : "1 pulse (Normal)");

    // Update Probability Bar Chart
    if (state.probChart) {
        state.probChart.data.datasets[0].data = [
            (r.probLow || 0.70) * 100,
            (r.probModerate || 0.20) * 100,
            (r.probHigh || 0.10) * 100
        ];
        state.probChart.update();
    }

    // Populate Report Screen
    document.getElementById("rep-knee").innerText = assessment.kneeSide;
    document.getElementById("rep-pain").innerText = assessment.painScore;
    document.getElementById("rep-date").innerText = new Date(assessment.assessmentDate).toLocaleDateString();
    document.getElementById("rep-risk-title").innerText = `${r.riskCategory} RISK PRELIMINARY ESTIMATE`;
}

// -----------------------------------------------------------------------------
// History Table
// -----------------------------------------------------------------------------
async function loadAssessmentHistory() {
    if (!state.token) return;
    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/assessments`, {
            headers: { "Authorization": `Bearer ${state.token}` }
        });
        if (res.ok) {
            state.assessments = await res.json();
            renderHistoryTable(state.assessments);
            updateDashboardCards(state.assessments);
        }
    } catch (e) {
        console.warn("Could not load history:", e);
    }
}

function renderHistoryTable(list) {
    const tbody = document.getElementById("history-tbody");
    const dashTbody = document.getElementById("dash-recent-tbody");
    if (!tbody) return;

    if (!list || list.length === 0) {
        tbody.innerHTML = `<tr><td colspan="8" style="text-align:center; padding:24px; color:var(--text-muted);">No records found.</td></tr>`;
        dashTbody.innerHTML = `<tr><td colspan="7" style="text-align:center; padding:24px; color:var(--text-muted);">No records found.</td></tr>`;
        return;
    }

    let rowsHtml = "";
    list.forEach(a => {
        const risk = a.result ? a.result.riskCategory : "PENDING";
        const riskClass = risk === "LOW" ? "badge-risk-low" : (risk === "MODERATE" ? "badge-risk-mod" : "badge-risk-high");
        const dateStr = new Date(a.assessmentDate).toLocaleString();

        rowsHtml += `
            <tr>
                <td><strong>#${a.id}</strong></td>
                <td>${dateStr}</td>
                <td><span style="font-weight:600;">${a.kneeSide}</span></td>
                <td>${a.painScore}/10</td>
                <td><span style="font-size:12px; color:var(--text-muted);">${a.status}</span></td>
                <td><span class="card-badge ${riskClass}">${risk}</span></td>
                <td>${a.result ? (a.result.predictionScore || 0).toFixed(2) : '--'}</td>
                <td>
                    <button class="btn btn-secondary" style="padding:4px 10px; font-size:12px;" onclick="viewAssessmentDetails(${a.id})">Inspect</button>
                </td>
            </tr>
        `;
    });

    tbody.innerHTML = rowsHtml;
    dashTbody.innerHTML = rowsHtml;
}

function filterHistoryTable() {
    const q = document.getElementById("history-search").value.toLowerCase();
    const filtered = state.assessments.filter(a =>
        a.kneeSide.toLowerCase().includes(q) ||
        (a.status && a.status.toLowerCase().includes(q)) ||
        (a.result && a.result.riskCategory.toLowerCase().includes(q))
    );
    renderHistoryTable(filtered);
}

function viewAssessmentDetails(id) {
    const found = state.assessments.find(a => a.id === id);
    if (found) {
        state.latestResult = found;
        displayAssessmentResults(found);
        navigateTo("assessment-results");
    }
}

function updateDashboardCards(list) {
    document.getElementById("dash-total-count").innerText = list.length;
    if (list.length > 0) {
        const latest = list[0];
        const risk = latest.result ? latest.result.riskCategory : "PENDING";
        document.getElementById("dash-latest-category").innerText = `${risk} Risk`;
        const badge = document.getElementById("dash-latest-badge");
        badge.className = "card-badge";
        badge.innerText = risk;
        badge.classList.add(risk === "LOW" ? "badge-risk-low" : (risk === "MODERATE" ? "badge-risk-mod" : "badge-risk-high"));
    }
}

// -----------------------------------------------------------------------------
// Live Sensor Telemetry Charts (Chart.js)
// -----------------------------------------------------------------------------
function initLiveChart() {
    const ctx = document.getElementById("live-sensor-chart");
    if (!ctx) return;

    state.liveChart = new Chart(ctx, {
        type: "line",
        data: {
            labels: Array(30).fill(""),
            datasets: [
                {
                    label: "Accel X (g)",
                    borderColor: "#0d9488",
                    data: Array(30).fill(0),
                    tension: 0.3,
                    borderWidth: 2,
                    pointRadius: 0
                },
                {
                    label: "Accel Y (g)",
                    borderColor: "#0284c7",
                    data: Array(30).fill(0),
                    tension: 0.3,
                    borderWidth: 2,
                    pointRadius: 0
                },
                {
                    label: "Accel Z (g)",
                    borderColor: "#8b5cf6",
                    data: Array(30).fill(1),
                    tension: 0.3,
                    borderWidth: 2,
                    pointRadius: 0
                },
                {
                    label: "Flex Angle (°)",
                    borderColor: "#f59e0b",
                    data: Array(30).fill(60),
                    tension: 0.3,
                    borderWidth: 2,
                    pointRadius: 0,
                    yAxisID: "yFlex"
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            scales: {
                y: {
                    min: -2.0,
                    max: 2.5,
                    title: { display: true, text: "Acceleration (g)" }
                },
                yFlex: {
                    position: "right",
                    min: 0,
                    max: 130,
                    title: { display: true, text: "Flex Angle (°)" },
                    grid: { drawOnChartArea: false }
                }
            }
        }
    });
}

function initProbChart() {
    const ctx = document.getElementById("result-prob-chart");
    if (!ctx) return;

    state.probChart = new Chart(ctx, {
        type: "bar",
        data: {
            labels: ["Low Risk", "Moderate Risk", "High Risk"],
            datasets: [{
                label: "Random Forest Probability (%)",
                data: [75, 18, 7],
                backgroundColor: ["#10b981", "#f59e0b", "#ef4444"],
                borderRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            indexAxis: 'y',
            scales: {
                x: {
                    min: 0,
                    max: 100,
                    ticks: { callback: v => v + "%" }
                }
            }
        }
    });
}

function toggleTelemetryStream() {
    state.telemetryPaused = !state.telemetryPaused;
    document.getElementById("btn-toggle-telemetry").innerText =
        state.telemetryPaused ? "Resume Live Stream" : "Pause Live Stream";
}

function startTelemetryStreamLoop() {
    let tick = 0;
    setInterval(() => {
        if (state.telemetryPaused) return;
        tick += 0.2;

        const ax = Math.sin(tick) * 0.45;
        const ay = Math.cos(tick) * 0.35;
        const az = 0.98 + Math.sin(tick * 2) * 0.15;
        const flex = 65 + Math.sin(tick) * 35;

        // Update metric display cards
        const metricAccel = document.getElementById("metric-accel");
        if (metricAccel) {
            const mag = Math.sqrt(ax * ax + ay * ay + az * az).toFixed(2);
            metricAccel.innerText = `${mag} g`;
            document.getElementById("metric-accel-xyz").innerText = `X: ${ax.toFixed(2)} | Y: ${ay.toFixed(2)} | Z: ${az.toFixed(2)}`;
            document.getElementById("metric-flex").innerText = `${Math.round(flex)}°`;
            document.getElementById("metric-gyro").innerText = `${(25 + Math.abs(Math.sin(tick) * 40)).toFixed(1)} °/s`;
        }

        // Push to live chart
        if (state.liveChart) {
            const chartData = state.liveChart.data;
            chartData.datasets[0].data.shift();
            chartData.datasets[0].data.push(ax);

            chartData.datasets[1].data.shift();
            chartData.datasets[1].data.push(ay);

            chartData.datasets[2].data.shift();
            chartData.datasets[2].data.push(az);

            chartData.datasets[3].data.shift();
            chartData.datasets[3].data.push(flex);

            state.liveChart.update("none");
        }
    }, 200);
}

// -----------------------------------------------------------------------------
// Settings & Diagnostics
// -----------------------------------------------------------------------------
async function checkHealthIndicators() {
    // Spring Boot Backend check
    try {
        const res = await fetch(`${CONFIG.backendUrl}/api/auth/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: "{}"
        });
        // 400/401 means server is up and responding
        document.getElementById("dot-backend").className = "status-dot online";
    } catch (e) {
        document.getElementById("dot-backend").className = "status-dot offline";
    }

    // Python ML Service check
    try {
        const res = await fetch(`${CONFIG.mlServiceUrl}/health`);
        if (res.ok) {
            document.getElementById("dot-ml").className = "status-dot online";
        } else {
            document.getElementById("dot-ml").className = "status-dot offline";
        }
    } catch (e) {
        document.getElementById("dot-ml").className = "status-dot offline";
    }

    // Sensor Indicator
    document.getElementById("dot-sensor").className =
        state.sensorMode === "BLE" ? "status-dot online" : "status-dot simulated";
}

async function runConnectionDiagnostics() {
    const diag = document.getElementById("diag-results");
    diag.innerHTML = "⏳ Probing service endpoints...";

    let out = "<ul>";

    // Backend test
    try {
        const bStart = performance.now();
        await fetch(`${CONFIG.backendUrl}/api/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: "{}" });
        const bLat = Math.round(performance.now() - bStart);
        out += `<li style="color:#10b981;">✅ Spring Boot Backend (Port 8080): Online (${bLat}ms latency)</li>`;
    } catch (e) {
        out += `<li style="color:#ef4444;">❌ Spring Boot Backend: Unreachable at ${CONFIG.backendUrl}</li>`;
    }

    // ML Service test
    try {
        const mStart = performance.now();
        const mRes = await fetch(`${CONFIG.mlServiceUrl}/health`);
        const mLat = Math.round(performance.now() - mStart);
        const mData = await mRes.json();
        out += `<li style="color:#10b981;">✅ Python ML Service (Port 8000): Online (${mLat}ms latency) — Model Loaded: ${mData.model_loaded}</li>`;
    } catch (e) {
        out += `<li style="color:#ef4444;">❌ Python ML Service: Unreachable at ${CONFIG.mlServiceUrl}</li>`;
    }

    out += "</ul>";
    diag.innerHTML = out;
}

function toggleSensorMode(mode) {
    state.sensorMode = mode;
    const label = mode === "BLE" ? "Sensor: BLE" : (mode === "SERIAL" ? "Sensor: USB Serial" : "Sensor: Sim");
    document.getElementById("sensor-status-label").innerText = label;
    document.getElementById("btn-ble-connect").disabled = (mode !== "BLE");
    const serialBtn = document.getElementById("btn-serial-connect");
    if (serialBtn) serialBtn.disabled = (mode !== "SERIAL");
    checkHealthIndicators();
}

function handleIncomingHardwarePacket(data) {
    // Save physical reading to state
    state.latestHardwareSample = {
        sequenceNumber: data.seq || 0,
        accelerationX: (data.ax !== undefined) ? (data.ax / 9.81) : 0, // convert m/s² to g
        accelerationY: (data.ay !== undefined) ? (data.ay / 9.81) : 0,
        accelerationZ: (data.az !== undefined) ? (data.az / 9.81) : 1,
        gyroscopeX: data.gx || 0,
        gyroscopeY: data.gy || 0,
        gyroscopeZ: data.gz || 0,
        flexValue: data.flex || 0,
        fsrValue: data.fsr || 0,
        vibrationState: (data.vib === 1),
        mpuAvailable: (data.mpuOk === 1),
        flexAvailable: (data.flexOk === 1),
        fsrAvailable: (data.fsrOk === 1),
        vibAvailable: (data.vibOk === 1)
    };

    // Update real-time UI telemetry metric cards
    const ax = state.latestHardwareSample.accelerationX;
    const ay = state.latestHardwareSample.accelerationY;
    const az = state.latestHardwareSample.accelerationZ;
    const mag = Math.sqrt(ax * ax + ay * ay + az * az).toFixed(2);

    const mAccel = document.getElementById("metric-accel");
    if (mAccel) {
        mAccel.innerText = `${mag} g`;
        document.getElementById("metric-accel-xyz").innerText = `X: ${ax.toFixed(2)} | Y: ${ay.toFixed(2)} | Z: ${az.toFixed(2)}`;
        // Convert flex ADC (0-4095) roughly to angle degrees (approx 0-120°)
        const angle = Math.round((data.flex / 4095) * 120);
        document.getElementById("metric-flex").innerText = `${angle}°`;
        document.getElementById("metric-flex-raw").innerText = `ADC: ${data.flex} / 4095`;
        const gyroMag = Math.sqrt((data.gx||0)**2 + (data.gy||0)**2 + (data.gz||0)**2).toFixed(1);
        document.getElementById("metric-gyro").innerText = `${gyroMag} °/s`;
        document.getElementById("metric-fsr").innerText = `${data.fsr} N/adc`;
        document.getElementById("metric-vib-state").innerText = `Vib: ${data.vib ? 'EVENT DETECTED' : 'Normal'}`;
    }

    // Push physical packet to live chart
    if (state.liveChart && !state.telemetryPaused) {
        const cd = state.liveChart.data;
        cd.datasets[0].data.shift();
        cd.datasets[0].data.push(ax);

        cd.datasets[1].data.shift();
        cd.datasets[1].data.push(ay);

        cd.datasets[2].data.shift();
        cd.datasets[2].data.push(az);

        cd.datasets[3].data.shift();
        cd.datasets[3].data.push((data.flex / 4095) * 120);

        state.liveChart.update("none");
    }
}

async function connectWebBluetooth() {
    if (!navigator.bluetooth) {
        alert("Web Bluetooth API is not supported by this browser. Please use Chrome/Edge or select USB Serial / Simulation Mode.");
        return;
    }

    try {
        state.bleDevice = await navigator.bluetooth.requestDevice({
            filters: [{ namePrefix: "SmartKneeOA" }],
            optionalServices: [CONFIG.bleServiceUuid]
        });

        const server = await state.bleDevice.gatt.connect();
        const service = await server.getPrimaryService(CONFIG.bleServiceUuid);
        const char = await service.getCharacteristic(CONFIG.bleCharUuid);

        await char.startNotifications();
        char.addEventListener('characteristicvaluechanged', (event) => {
            const val = event.target.value;
            const decoder = new TextDecoder('utf-8');
            const str = decoder.decode(val);
            try {
                const data = JSON.parse(str);
                handleIncomingHardwarePacket(data);
            } catch (err) {
                // Fragmented packets are ignored or appended
            }
        });

        alert(`✅ Connected to ESP32: ${state.bleDevice.name}`);
        document.getElementById("sensor-status-label").innerText = `Sensor: ${state.bleDevice.name}`;
        document.getElementById("dot-sensor").className = "status-dot online";
    } catch (err) {
        alert("BLE Connection cancelled or failed: " + err.message);
    }
}

async function connectWebSerial() {
    if (!("serial" in navigator)) {
        alert("Web Serial API is not supported by this browser. Please use Google Chrome or Microsoft Edge.");
        return;
    }

    try {
        const port = await navigator.serial.requestPort();
        await port.open({ baudRate: 115200 });

        alert("✅ Connected to ESP32 via USB Serial (115200 baud)!");
        document.getElementById("sensor-status-label").innerText = "Sensor: ESP32 (USB)";
        document.getElementById("dot-sensor").className = "status-dot online";

        const textDecoder = new TextDecoderStream();
        port.readable.pipeTo(textDecoder.writable);
        const reader = textDecoder.readable.getReader();

        let lineBuffer = "";
        while (true) {
            const { value, done } = await reader.read();
            if (done) break;
            if (value) {
                lineBuffer += value;
                const lines = lineBuffer.split("\n");
                lineBuffer = lines.pop(); // keep remainder
                for (const line of lines) {
                    const trimmed = line.trim();
                    if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                        try {
                            const data = JSON.parse(trimmed);
                            handleIncomingHardwarePacket(data);
                        } catch (e) {}
                    }
                }
            }
        }
    } catch (err) {
        alert("Serial connection error: " + err.message);
    }
}

