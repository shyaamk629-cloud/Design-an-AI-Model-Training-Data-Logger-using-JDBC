package com.ailogger.model;

import java.time.LocalDateTime;

/**
 * Logs any change to hyperparameters during training (e.g. LR scheduling).
 * Maps to the {@code hyperparameter_log} table.
 */
public class HyperparameterLog {

    private long          logId;
    private String        sessionId;
    private Integer       epochNumber;    // null = pre-training change
    private String        paramName;      // e.g. "learning_rate", "dropout"
    private String        oldValue;
    private String        newValue;
    private String        reason;         // e.g. "ReduceLROnPlateau triggered"
    private LocalDateTime changedAt;

    // ── Constructors ──────────────────────────────────────────────────────────
    public HyperparameterLog() {}

    public HyperparameterLog(String sessionId, Integer epochNumber,
                             String paramName, String oldValue,
                             String newValue, String reason) {
        this.sessionId   = sessionId;
        this.epochNumber = epochNumber;
        this.paramName   = paramName;
        this.oldValue    = oldValue;
        this.newValue    = newValue;
        this.reason      = reason;
        this.changedAt   = LocalDateTime.now();
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────
    public long          getLogId()        { return logId; }
    public void          setLogId(long v)         { this.logId = v; }

    public String        getSessionId()    { return sessionId; }
    public void          setSessionId(String v)   { this.sessionId = v; }

    public Integer       getEpochNumber()  { return epochNumber; }
    public void          setEpochNumber(Integer v) { this.epochNumber = v; }

    public String        getParamName()    { return paramName; }
    public void          setParamName(String v)   { this.paramName = v; }

    public String        getOldValue()     { return oldValue; }
    public void          setOldValue(String v)    { this.oldValue = v; }

    public String        getNewValue()     { return newValue; }
    public void          setNewValue(String v)    { this.newValue = v; }

    public String        getReason()       { return reason; }
    public void          setReason(String v)      { this.reason = v; }

    public LocalDateTime getChangedAt()    { return changedAt; }
    public void          setChangedAt(LocalDateTime v) { this.changedAt = v; }

    @Override
    public String toString() {
        return String.format(
            "HparamChange{epoch=%s, param='%s', %s → %s, reason='%s'}",
            epochNumber, paramName, oldValue, newValue, reason);
    }
}
