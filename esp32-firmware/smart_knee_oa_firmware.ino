/*
 * Smart Knee OA Assessment System – ESP32 Sensor Firmware
 * =========================================================
 * Board   : ESP32 DevKit V1
 * IDE     : Arduino IDE 2.x  |  Platform: espressif32
 *
 * Sensors & Pin Mapping
 * ─────────────────────
 *  MPU6050   SDA ── GPIO 21
 *  MPU6050   SCL ── GPIO 22
 *  Flex      ADC ── GPIO 34  (input-only, voltage-divider required)
 *  FSR       ADC ── GPIO 35  (input-only, voltage-divider required)
 *  VibSensor DO  ── GPIO 32
 *
 * BLE UUIDs  (shared with Java front-end)
 * ─────────────────────────────────────────
 *  Service Char : 4FAFC201-1FB5-459E-8FCC-C5C9C331914B
 *  Sensor Char  : BEB5483E-36E1-4688-B7F5-EA07361B26A8
 *
 * Wiring notes
 * ─────────────
 *  - Flex : 3.3 V ── 10 kΩ ── GPIO 34 ── (Flex) ── GND
 *  - FSR  : 3.3 V ── FSR ── GPIO 35 ── 10 kΩ ── GND
 *  - MPU6050 operates at 3.3 V (I2C pull-ups included on most breakout boards)
 *  - Vibration sensor: VCC=3.3 V, GND=GND, DO=GPIO 32
 *
 * Libraries required (install via Arduino Library Manager)
 * ──────────────────────────────────────────────────────────
 *  - "MPU6050" by Electronic Cats   (or Adafruit MPU6050)
 *  - "Wire"  (built-in)
 *  - "BLEDevice" (built-in ESP32 Arduino)
 */

#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEUtils.h>
#include <BLEServer.h>
#include <BLE2902.h>
#include <MPU6050.h>

// ─────────────────────────────────────────────────────────────────────────────
// Configuration
// ─────────────────────────────────────────────────────────────────────────────
#define DEVICE_NAME         "SmartKneeOA"
#define SERVICE_UUID        "4FAFC201-1FB5-459E-8FCC-C5C9C331914B"
#define SENSOR_CHAR_UUID    "BEB5483E-36E1-4688-B7F5-EA07361B26A8"

#define PIN_FLEX            34
#define PIN_FSR             35
#define PIN_VIBRATION       32

#define SAMPLE_INTERVAL_MS  100   // 10 Hz
#define ADC_MAX             4095
#define RECONNECT_DELAY_MS  500

// ─────────────────────────────────────────────────────────────────────────────
// Globals
// ─────────────────────────────────────────────────────────────────────────────
MPU6050          mpu;
BLEServer*       pServer     = nullptr;
BLECharacteristic* pSensorChar = nullptr;

bool     deviceConnected    = false;
bool     mpuAvailable       = false;
bool     flexAvailable      = true;
bool     fsrAvailable       = true;
bool     vibAvailable       = true;

uint32_t sequenceNumber     = 0;
uint32_t lastSampleTime     = 0;

// ─────────────────────────────────────────────────────────────────────────────
// BLE Callbacks
// ─────────────────────────────────────────────────────────────────────────────
class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer* svr) override {
        deviceConnected = true;
        Serial.println("[BLE] Client connected.");
    }
    void onDisconnect(BLEServer* svr) override {
        deviceConnected = false;
        Serial.println("[BLE] Client disconnected. Restarting advertising...");
        delay(RECONNECT_DELAY_MS);
        svr->startAdvertising();
    }
};

// ─────────────────────────────────────────────────────────────────────────────
// Setup
// ─────────────────────────────────────────────────────────────────────────────
void setup() {
    Serial.begin(115200);
    Serial.println("\n=== Smart Knee OA – Sensor Node Starting ===");

    // Vibration sensor pin
    pinMode(PIN_VIBRATION, INPUT);

    // MPU6050 initialisation
    Wire.begin(21, 22);
    mpu.initialize();
    if (mpu.testConnection()) {
        mpuAvailable = true;
        mpu.setFullScaleAccelRange(MPU6050_ACCEL_FS_4);   // ±4 g
        mpu.setFullScaleGyroRange(MPU6050_GYRO_FS_500);   // ±500 °/s
        Serial.println("[MPU6050] Connected.");
    } else {
        mpuAvailable = false;
        Serial.println("[MPU6050] NOT connected – readings will be marked unavailable.");
    }

    // ADC resolution (ESP32 default is 12-bit: 0-4095)
    analogReadResolution(12);

    // BLE initialisation
    BLEDevice::init(DEVICE_NAME);
    pServer = BLEDevice::createServer();
    pServer->setCallbacks(new ServerCallbacks());

    BLEService* pService = pServer->createService(SERVICE_UUID);

    pSensorChar = pService->createCharacteristic(
        SENSOR_CHAR_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );
    pSensorChar->addDescriptor(new BLE2902());

    pService->start();

    BLEAdvertising* pAdvert = BLEDevice::getAdvertising();
    pAdvert->addServiceUUID(SERVICE_UUID);
    pAdvert->setScanResponse(true);
    pAdvert->setMinPreferred(0x06);
    BLEDevice::startAdvertising();

    Serial.println("[BLE] Advertising started. Waiting for connection...");
}

// ─────────────────────────────────────────────────────────────────────────────
// Sensor reading helpers
// ─────────────────────────────────────────────────────────────────────────────
struct SensorPacket {
    uint32_t seq;
    // MPU6050
    float    ax, ay, az;   // m/s²
    float    gx, gy, gz;   // °/s
    // Flex / FSR (raw ADC 0-4095)
    uint16_t flex;
    uint16_t fsr;
    // Vibration
    uint8_t  vibration;
    // Availability flags
    uint8_t  mpuOk;
    uint8_t  flexOk;
    uint8_t  fsrOk;
    uint8_t  vibOk;
};

/**
 * Read MPU6050 and convert raw values to SI units.
 *  Accel scale: ±4 g  →  sensitivity = 8192 LSB/g  →  multiply by 9.81/8192
 *  Gyro  scale: ±500°/s → sensitivity = 65.5 LSB/(°/s) → divide by 65.5
 */
void readMPU(SensorPacket& pkt) {
    int16_t rawAx, rawAy, rawAz, rawGx, rawGy, rawGz;
    mpu.getMotion6(&rawAx, &rawAy, &rawAz, &rawGx, &rawGy, &rawGz);

    pkt.ax = rawAx / 8192.0f * 9.81f;
    pkt.ay = rawAy / 8192.0f * 9.81f;
    pkt.az = rawAz / 8192.0f * 9.81f;
    pkt.gx = rawGx / 65.5f;
    pkt.gy = rawGy / 65.5f;
    pkt.gz = rawGz / 65.5f;
}

void collectAndTransmit() {
    SensorPacket pkt = {};
    pkt.seq  = sequenceNumber++;

    // MPU6050
    if (mpuAvailable) {
        readMPU(pkt);
        pkt.mpuOk = 1;
    }

    // Flex sensor
    pkt.flex   = (uint16_t) analogRead(PIN_FLEX);
    pkt.flexOk = 1;

    // FSR
    pkt.fsr    = (uint16_t) analogRead(PIN_FSR);
    pkt.fsrOk  = 1;

    // Vibration
    pkt.vibration = (uint8_t)(digitalRead(PIN_VIBRATION) == HIGH ? 1 : 0);
    pkt.vibOk     = 1;

    // ── Build compact JSON string ──────────────────────────────────────────
    // JSON is human-readable and easy to parse on the Java frontend.
    char json[320];
    snprintf(json, sizeof(json),
        "{\"seq\":%lu,"
        "\"ax\":%.4f,\"ay\":%.4f,\"az\":%.4f,"
        "\"gx\":%.4f,\"gy\":%.4f,\"gz\":%.4f,"
        "\"flex\":%u,\"fsr\":%u,\"vib\":%u,"
        "\"mpuOk\":%u,\"flexOk\":%u,\"fsrOk\":%u,\"vibOk\":%u}",
        (unsigned long)pkt.seq,
        pkt.ax, pkt.ay, pkt.az,
        pkt.gx, pkt.gy, pkt.gz,
        pkt.flex, pkt.fsr, pkt.vibration,
        pkt.mpuOk, pkt.flexOk, pkt.fsrOk, pkt.vibOk
    );

    if (deviceConnected && pSensorChar != nullptr) {
        pSensorChar->setValue((uint8_t*)json, strlen(json));
        pSensorChar->notify();
    }

    // Always output JSON to Serial for USB / Web Serial support
    Serial.println(json);
}

// ─────────────────────────────────────────────────────────────────────────────
// Main loop
// ─────────────────────────────────────────────────────────────────────────────
void loop() {
    uint32_t now = millis();
    if (now - lastSampleTime >= SAMPLE_INTERVAL_MS) {
        lastSampleTime = now;
        collectAndTransmit();
    }
}
