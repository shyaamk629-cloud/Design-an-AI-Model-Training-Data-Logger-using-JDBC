package com.ailogger.model;

import java.time.LocalDateTime;

/**
 * Represents a saved model checkpoint at a specific epoch.
 * Maps to the {@code model_checkpoints} table.
 */
public class ModelCheckpoint {

    private long          checkpointId;
    private String        sessionId;
    private int           epochNumber;
    private String        filePath;         // path to saved model weights
    private double        valLoss;
    private double        valAccuracy;
    private boolean       isBest;           // true if best checkpoint so far
    private LocalDateTime savedAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public ModelCheckpoint() {}

    public ModelCheckpoint(String sessionId, int epochNumber,
                           String filePath, double valLoss, double valAccuracy) {
        this.sessionId   = sessionId;
        this.epochNumber = epochNumber;
        this.filePath    = filePath;
        this.valLoss     = valLoss;
        this.valAccuracy = valAccuracy;
        this.savedAt     = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public long          getCheckpointId()   { return checkpointId; }
    public void          setCheckpointId(long v)       { this.checkpointId = v; }

    public String        getSessionId()      { return sessionId; }
    public void          setSessionId(String v)        { this.sessionId = v; }

    public int           getEpochNumber()    { return epochNumber; }
    public void          setEpochNumber(int v)          { this.epochNumber = v; }

    public String        getFilePath()       { return filePath; }
    public void          setFilePath(String v)         { this.filePath = v; }

    public double        getValLoss()        { return valLoss; }
    public void          setValLoss(double v)           { this.valLoss = v; }

    public double        getValAccuracy()    { return valAccuracy; }
    public void          setValAccuracy(double v)       { this.valAccuracy = v; }

    public boolean       isBest()            { return isBest; }
    public void          setBest(boolean v)             { this.isBest = v; }

    public LocalDateTime getSavedAt()        { return savedAt; }
    public void          setSavedAt(LocalDateTime v)   { this.savedAt = v; }

    @Override
    public String toString() {
        return String.format(
            "Checkpoint{epoch=%d, valLoss=%.4f, valAcc=%.2f%%, best=%s, path='%s'}",
            epochNumber, valLoss, valAccuracy * 100, isBest, filePath);
    }
}
