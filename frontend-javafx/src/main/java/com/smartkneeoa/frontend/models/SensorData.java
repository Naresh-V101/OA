package com.smartkneeoa.frontend.models;

import java.time.LocalDateTime;

/** Represents a single BLE sensor reading packet from the ESP32. */
public class SensorData {
    private int     sequenceNumber;
    private double  accelerationX;
    private double  accelerationY;
    private double  accelerationZ;
    private double  gyroscopeX;
    private double  gyroscopeY;
    private double  gyroscopeZ;
    private int     flexValue;
    private int     fsrValue;
    private boolean vibrationState;
    private boolean mpuAvailable  = true;
    private boolean flexAvailable = true;
    private boolean fsrAvailable  = true;
    private boolean vibAvailable  = true;
    private LocalDateTime timestamp = LocalDateTime.now();

    // ── Getters / Setters ──────────────────────────────────────
    public int     getSequenceNumber()  { return sequenceNumber; }
    public void    setSequenceNumber(int v) { sequenceNumber = v; }
    public double  getAccelerationX()   { return accelerationX; }
    public void    setAccelerationX(double v) { accelerationX = v; }
    public double  getAccelerationY()   { return accelerationY; }
    public void    setAccelerationY(double v) { accelerationY = v; }
    public double  getAccelerationZ()   { return accelerationZ; }
    public void    setAccelerationZ(double v) { accelerationZ = v; }
    public double  getGyroscopeX()      { return gyroscopeX; }
    public void    setGyroscopeX(double v) { gyroscopeX = v; }
    public double  getGyroscopeY()      { return gyroscopeY; }
    public void    setGyroscopeY(double v) { gyroscopeY = v; }
    public double  getGyroscopeZ()      { return gyroscopeZ; }
    public void    setGyroscopeZ(double v) { gyroscopeZ = v; }
    public int     getFlexValue()       { return flexValue; }
    public void    setFlexValue(int v)  { flexValue = v; }
    public int     getFsrValue()        { return fsrValue; }
    public void    setFsrValue(int v)   { fsrValue = v; }
    public boolean isVibrationState()   { return vibrationState; }
    public void    setVibrationState(boolean v) { vibrationState = v; }
    public boolean isMpuAvailable()     { return mpuAvailable; }
    public void    setMpuAvailable(boolean v) { mpuAvailable = v; }
    public boolean isFlexAvailable()    { return flexAvailable; }
    public void    setFlexAvailable(boolean v) { flexAvailable = v; }
    public boolean isFsrAvailable()     { return fsrAvailable; }
    public void    setFsrAvailable(boolean v) { fsrAvailable = v; }
    public boolean isVibAvailable()     { return vibAvailable; }
    public void    setVibAvailable(boolean v) { vibAvailable = v; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void    setTimestamp(LocalDateTime v) { timestamp = v; }
}
