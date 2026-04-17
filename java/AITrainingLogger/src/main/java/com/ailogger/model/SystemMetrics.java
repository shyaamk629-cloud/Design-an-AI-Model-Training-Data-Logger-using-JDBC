package com.ailogger.model;

import java.time.LocalDateTime;

/**
 * Captures hardware/resource usage at a given point in training.
 * Maps to the {@code system_metrics} table.
 */
public class SystemMetrics {

    private long          metricId;
    private String        sessionId;
    private Integer       epochNumber;
    private double        cpuUsagePct;     // 0–100
    private double        ramUsedMb;       // MB
    private double        gpuUsagePct;     // 0–100 (0 if no GPU)
    private double        gpuMemoryMb;     // MB
    private LocalDateTime recordedAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public SystemMetrics() {}

    public SystemMetrics(String sessionId, Integer epochNumber,
                         double cpuUsagePct, double ramUsedMb,
                         double gpuUsagePct, double gpuMemoryMb) {
        this.sessionId    = sessionId;
        this.epochNumber  = epochNumber;
        this.cpuUsagePct  = cpuUsagePct;
        this.ramUsedMb    = ramUsedMb;
        this.gpuUsagePct  = gpuUsagePct;
        this.gpuMemoryMb  = gpuMemoryMb;
        this.recordedAt   = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public long          getMetricId()      { return metricId; }
    public void          setMetricId(long v)        { this.metricId = v; }

    public String        getSessionId()     { return sessionId; }
    public void          setSessionId(String v)     { this.sessionId = v; }

    public Integer       getEpochNumber()   { return epochNumber; }
    public void          setEpochNumber(Integer v)  { this.epochNumber = v; }

    public double        getCpuUsagePct()   { return cpuUsagePct; }
    public void          setCpuUsagePct(double v)   { this.cpuUsagePct = v; }

    public double        getRamUsedMb()     { return ramUsedMb; }
    public void          setRamUsedMb(double v)     { this.ramUsedMb = v; }

    public double        getGpuUsagePct()   { return gpuUsagePct; }
    public void          setGpuUsagePct(double v)   { this.gpuUsagePct = v; }

    public double        getGpuMemoryMb()   { return gpuMemoryMb; }
    public void          setGpuMemoryMb(double v)   { this.gpuMemoryMb = v; }

    public LocalDateTime getRecordedAt()    { return recordedAt; }
    public void          setRecordedAt(LocalDateTime v) { this.recordedAt = v; }

    @Override
    public String toString() {
        return String.format(
            "SystemMetrics{epoch=%s, CPU=%.1f%%, RAM=%.0fMB, GPU=%.1f%%, VRAM=%.0fMB}",
            epochNumber, cpuUsagePct, ramUsedMb, gpuUsagePct, gpuMemoryMb);
    }
}
