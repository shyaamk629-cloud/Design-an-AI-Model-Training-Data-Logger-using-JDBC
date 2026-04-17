package com.ailogger.model;

import java.time.LocalDateTime;

/**
 * Represents one complete AI model training run / session.
 *
 * Maps to the {@code training_sessions} table.
 */
public class TrainingSession {

    // ── Status enum ───────────────────────────────────────────────────────────
    public enum Status { RUNNING, COMPLETED, FAILED, PAUSED }

    // ── Fields ────────────────────────────────────────────────────────────────
    private String        sessionId;       // UUID primary key
    private String        modelName;       // e.g. "ResNet-50"
    private String        modelVersion;    // e.g. "v2.1.0"
    private String        datasetName;     // e.g. "ImageNet-2024"
    private int           datasetSize;     // number of training samples
    private String        architecture;    // e.g. "CNN", "Transformer"
    private String        framework;       // e.g. "PyTorch", "TensorFlow"
    private String        optimizer;       // e.g. "Adam"
    private double        learningRate;
    private int           batchSize;
    private int           totalEpochs;
    private Status        status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;         // null while running
    private String        notes;

    // ── Constructors ──────────────────────────────────────────────────────────
    public TrainingSession() {}

    public TrainingSession(String sessionId, String modelName, String modelVersion,
                           String datasetName, int datasetSize) {
        this.sessionId   = sessionId;
        this.modelName   = modelName;
        this.modelVersion= modelVersion;
        this.datasetName = datasetName;
        this.datasetSize = datasetSize;
        this.status      = Status.RUNNING;
        this.startTime   = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public String        getSessionId()    { return sessionId; }
    public void          setSessionId(String v)    { this.sessionId = v; }

    public String        getModelName()    { return modelName; }
    public void          setModelName(String v)    { this.modelName = v; }

    public String        getModelVersion() { return modelVersion; }
    public void          setModelVersion(String v) { this.modelVersion = v; }

    public String        getDatasetName()  { return datasetName; }
    public void          setDatasetName(String v)  { this.datasetName = v; }

    public int           getDatasetSize()  { return datasetSize; }
    public void          setDatasetSize(int v)     { this.datasetSize = v; }

    public String        getArchitecture() { return architecture; }
    public void          setArchitecture(String v) { this.architecture = v; }

    public String        getFramework()    { return framework; }
    public void          setFramework(String v)    { this.framework = v; }

    public String        getOptimizer()    { return optimizer; }
    public void          setOptimizer(String v)    { this.optimizer = v; }

    public double        getLearningRate() { return learningRate; }
    public void          setLearningRate(double v) { this.learningRate = v; }

    public int           getBatchSize()    { return batchSize; }
    public void          setBatchSize(int v)       { this.batchSize = v; }

    public int           getTotalEpochs()  { return totalEpochs; }
    public void          setTotalEpochs(int v)     { this.totalEpochs = v; }

    public Status        getStatus()       { return status; }
    public void          setStatus(Status v)       { this.status = v; }

    public LocalDateTime getStartTime()    { return startTime; }
    public void          setStartTime(LocalDateTime v) { this.startTime = v; }

    public LocalDateTime getEndTime()      { return endTime; }
    public void          setEndTime(LocalDateTime v)   { this.endTime = v; }

    public String        getNotes()        { return notes; }
    public void          setNotes(String v)        { this.notes = v; }

    // ── toString ──────────────────────────────────────────────────────────────
    @Override
    public String toString() {
        return String.format(
            "TrainingSession{id='%s', model='%s %s', dataset='%s'(%d), " +
            "framework='%s', optimizer='%s', lr=%.5f, batch=%d, epochs=%d, status=%s}",
            sessionId, modelName, modelVersion, datasetName, datasetSize,
            framework, optimizer, learningRate, batchSize, totalEpochs, status);
    }
}
