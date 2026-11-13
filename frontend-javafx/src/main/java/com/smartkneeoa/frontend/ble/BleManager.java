package com.smartkneeoa.frontend.ble;

import com.smartkneeoa.frontend.models.SensorData;
import javafx.application.Platform;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * BLE Manager for the Smart Knee OA JavaFX frontend.
 *
 * On desktop (non-Android) Java, native BLE requires a platform bridge.
 * This implementation uses a two-mode design:
 *
 *  1. REAL MODE – calls the native BLE bridge (or a future JNI layer).
 *     Stubbed here; integrate your platform-specific BLE stack (e.g., 
 *     Bluetooth LE for Java via tinyb / bluez-dbus-jni on Linux,
 *     or JavaBluetooth on Windows/macOS).
 *
 *  2. SIMULATION MODE (default) – generates realistic synthetic sensor
 *     readings for development and demonstration without hardware.
 *
 * The BLE UUIDs MUST match the ESP32 firmware:
 *   Service  : 4FAFC201-1FB5-459E-8FCC-C5C9C331914B
 *   Char     : BEB5483E-36E1-4688-B7F5-EA07361B26A8
 */
public class BleManager {

    private static final Logger log = Logger.getLogger(BleManager.class.getName());

    public static final String SERVICE_UUID  = "4FAFC201-1FB5-459E-8FCC-C5C9C331914B";
    public static final String CHAR_UUID     = "BEB5483E-36E1-4688-B7F5-EA07361B26A8";
    public static final String DEVICE_NAME   = "SmartKneeOA";

    // ── Singleton ────────────────────────────────────────────────
    private static final BleManager INSTANCE = new BleManager();
    public  static BleManager getInstance() { return INSTANCE; }

    // ── State ────────────────────────────────────────────────────
    public enum ConnectionState { DISCONNECTED, SCANNING, CONNECTING, CONNECTED }

    private volatile ConnectionState state = ConnectionState.DISCONNECTED;

    private final AtomicBoolean simulationMode = new AtomicBoolean(true);
    private final List<Consumer<SensorData>> dataListeners  = new CopyOnWriteArrayList<>();
    private final List<Consumer<ConnectionState>> stateListeners = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService simExecutor;
    private int simSeq = 0;
    private final Random rng = new Random();

    // ── Connect ──────────────────────────────────────────────────
    public void connect() {
        if (simulationMode.get()) {
            startSimulation();
        } else {
            startRealBle();
        }
    }

    public void disconnect() {
        setState(ConnectionState.DISCONNECTED);
        if (simExecutor != null && !simExecutor.isShutdown()) {
            simExecutor.shutdownNow();
        }
        log.info("[BLE] Disconnected.");
    }

    // ── Simulation ───────────────────────────────────────────────
    private void startSimulation() {
        setState(ConnectionState.SCANNING);
        log.info("[BLE-SIM] Starting simulated BLE sensor stream...");

        simExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ble-sim");
            t.setDaemon(true);
            return t;
        });

        // Simulate connection delay (600 ms)
        simExecutor.schedule(() -> {
            setState(ConnectionState.CONNECTED);
            log.info("[BLE-SIM] Simulated device connected.");
        }, 600, TimeUnit.MILLISECONDS);

        // Stream at 10 Hz
        simExecutor.scheduleAtFixedRate(this::emitSimulatedReading,
                700, 100, TimeUnit.MILLISECONDS);
    }

    private void emitSimulatedReading() {
        if (state != ConnectionState.CONNECTED) return;

        double t = simSeq * 0.1;  // seconds
        SensorData data = new SensorData();
        data.setSequenceNumber(simSeq++);

        // Simulate walking pattern: sinusoidal motion
        data.setAccelerationX(0.02  + 0.25 * Math.sin(2 * Math.PI * 0.8 * t) + noise(0.05));
        data.setAccelerationY(-0.05 + 0.15 * Math.cos(2 * Math.PI * 0.8 * t) + noise(0.03));
        data.setAccelerationZ(9.81  + 0.10 * Math.sin(2 * Math.PI * 1.6 * t) + noise(0.02));
        data.setGyroscopeX(0.5  * Math.sin(2 * Math.PI * 0.8 * t) + noise(0.1));
        data.setGyroscopeY(0.3  * Math.cos(2 * Math.PI * 0.8 * t) + noise(0.05));
        data.setGyroscopeZ(0.1  * Math.sin(2 * Math.PI * 1.6 * t) + noise(0.02));
        data.setFlexValue(2200 + (int)(300 * Math.sin(2 * Math.PI * 0.4 * t)) + (int) noise(50));
        data.setFsrValue(1800 + (int)(400 * Math.abs(Math.sin(2 * Math.PI * 0.8 * t))) + (int) noise(80));
        data.setVibrationState(rng.nextDouble() < 0.05);  // 5% chance of vibration event

        data.setMpuAvailable(true);
        data.setFlexAvailable(true);
        data.setFsrAvailable(true);
        data.setVibAvailable(true);

        notifyListeners(data);
    }

    private double noise(double scale) {
        return (rng.nextDouble() - 0.5) * 2 * scale;
    }

    // ── Real BLE (stub – integrate your BLE library here) ────────
    private void startRealBle() {
        setState(ConnectionState.SCANNING);
        log.info("[BLE] Scanning for device: " + DEVICE_NAME);
        // TODO: Integrate tinyb / BluezDbus / JavaBluetooth here.
        // On successful characteristic notification, call:
        //   notifyListeners(parseSensorJson(characteristicValue));
        // On disconnect, call setState(ConnectionState.DISCONNECTED).
        log.warning("[BLE] Real BLE not implemented on this platform. Switch to simulation mode.");
        setState(ConnectionState.DISCONNECTED);
    }

    // ── Listeners ────────────────────────────────────────────────
    public void addDataListener(Consumer<SensorData> listener) {
        dataListeners.add(listener);
    }

    public void removeDataListener(Consumer<SensorData> listener) {
        dataListeners.remove(listener);
    }

    public void addStateListener(Consumer<ConnectionState> listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Consumer<ConnectionState> listener) {
        stateListeners.remove(listener);
    }

    private void notifyListeners(SensorData data) {
        Platform.runLater(() -> dataListeners.forEach(l -> l.accept(data)));
    }

    private void setState(ConnectionState newState) {
        state = newState;
        Platform.runLater(() -> stateListeners.forEach(l -> l.accept(newState)));
    }

    // ── Accessors ─────────────────────────────────────────────────
    public ConnectionState getState()             { return state; }
    public boolean isConnected()                  { return state == ConnectionState.CONNECTED; }
    public void setSimulationMode(boolean sim)    { simulationMode.set(sim); }
    public boolean isSimulationMode()             { return simulationMode.get(); }
}
