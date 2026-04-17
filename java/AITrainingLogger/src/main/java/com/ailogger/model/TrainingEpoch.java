package com.ailogger.model;

import java.time.LocalDateTime;

/**
 * Stores per-epoch training metrics for a session.
 * Maps to the {@code training_epochs} table.
 */
public class TrainingEpoch {

    private long          epochId;          // auto-generated PK
    private String        sessionId;        // FK → training_sessions
    private int           epochNumber;      // 1-based epoch counter
    private double        trainLoss;        // cross-entropy / MSE loss
    private double        valLoss;          // validation loss
    private double        trainAccuracy;    // 0.0–1.0
    private double        valAccuracy;      // 0.0–1.0
    private double        trainF1;          // F1-score on train set
    private double        valF1;            // F1-score on val set
    private double        learningRate;     // effective LR at this epoch
    private long          epochDurationMs;  // wall-clock time in ms
    private LocalDateTime loggedAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public TrainingEpoch() {}

    public TrainingEpoch(String sessionId, int epochNumber,
                         double trainLoss, double valLoss,
                         double trainAccuracy, double valAccuracy) {
        this.sessionId     = sessionId;
        this.epochNumber   = epochNumber;
        this.trainLoss     = trainLoss;
        this.valLoss       = valLoss;
        this.trainAccuracy = trainAccuracy;
        this.valAccuracy   = valAccuracy;
        this.loggedAt      = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public long          getEpochId()          { return epochId; }
    public void          setEpochId(long v)           { this.epochId = v; }

    public String        getSessionId()        { return sessionId; }
    public void          setSessionId(String v)       { this.sessionId = v; }

    public int           getEpochNumber()      { return epochNumber; }
    public void          setEpochNumber(int v)         { this.epochNumber = v; }

    public double        getTrainLoss()        { return trainLoss; }
    public void          setTrainLoss(double v)        { this.trainLoss = v; }

    public double        getValLoss()          { return valLoss; }
    public void          setValLoss(double v)          { this.valLoss = v; }

    public double        getTrainAccuracy()    { return trainAccuracy; }
    public void          setTrainAccuracy(double v)    { this.trainAccuracy = v; }

    public double        getValAccuracy()      { return valAccuracy; }
    public void          setValAccuracy(double v)      { this.valAccuracy = v; }

    public double        getTrainF1()          { return trainF1; }
    public void          setTrainF1(double v)          { this.trainF1 = v; }

    public double        getValF1()            { return valF1; }
    public void          setValF1(double v)            { this.valF1 = v; }

    public double        getLearningRate()     { return learningRate; }
    public void          setLearningRate(double v)     { this.learningRate = v; }

    public long          getEpochDurationMs()  { return epochDurationMs; }
    public void          setEpochDurationMs(long v)    { this.epochDurationMs = v; }

    public LocalDateTime getLoggedAt()         { return loggedAt; }
    public void          setLoggedAt(LocalDateTime v)  { this.loggedAt = v; }

    // ── toString ──────────────────────────────────────────────────────────────
    @Override
    public String toString() {
        return String.format(
            "Epoch[%3d] trainLoss=%.4f valLoss=%.4f trainAcc=%.2f%% valAcc=%.2f%% " +
            "trainF1=%.4f valF1=%.4f lr=%.6f duration=%dms",
            epochNumber, trainLoss, valLoss,
            trainAccuracy * 100, valAccuracy * 100,
            trainF1, valF1, learningRate, epochDurationMs);
    }
}
