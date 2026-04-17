package com.ailogger.service;

import com.ailogger.dao.*;
import com.ailogger.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ═══════════════════════════════════════════════════════════════
 *  TrainingLogger — High-Level Service API
 * ═══════════════════════════════════════════════════════════════
 *
 * This is the primary entry-point for all logging operations.
 * It composes the five DAO objects and exposes a fluent, easy-to-use
 * API that hides all JDBC details from the caller.
 *
 * Typical usage pattern:
 * <pre>
 *   TrainingLogger logger = new TrainingLogger();
 *   String sessionId = logger.startSession(session);
 *
 *   for (int epoch = 1; epoch <= 50; epoch++) {
 *       // ... run training epoch ...
 *       logger.logEpoch(epoch);
 *       logger.logSystemMetrics(metrics);
 *       if (bestSoFar) logger.saveCheckpoint(ckpt);
 *   }
 *
 *   logger.completeSession(sessionId);
 * </pre>
 */
public class TrainingLogger {

    private static final Logger log = LoggerFactory.getLogger(TrainingLogger.class);

    // ── DAO dependencies ──────────────────────────────────────────────────────
    private final TrainingSessionDAO   sessionDAO    = new TrainingSessionDAO();
    private final TrainingEpochDAO     epochDAO      = new TrainingEpochDAO();
    private final ModelCheckpointDAO   checkpointDAO = new ModelCheckpointDAO();
    private final HyperparameterLogDAO hparamDAO     = new HyperparameterLogDAO();
    private final SystemMetricsDAO     sysMetricsDAO = new SystemMetricsDAO();

    // ── Best-loss tracker per session (in-memory for current run) ─────────────
    private double bestValLoss = Double.MAX_VALUE;

    // ═════════════════════════════════════════════════════════════════════════
    //  SESSION MANAGEMENT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Starts a new training session and persists it to the database.
     * A UUID is auto-assigned to the session.
     *
     * @param session pre-configured TrainingSession (status will be set to RUNNING)
     * @return the generated session UUID string
     */
    public String startSession(TrainingSession session) {
        if (session.getSessionId() == null || session.getSessionId().isBlank()) {
            session.setSessionId(UUID.randomUUID().toString());
        }
        session.setStatus(TrainingSession.Status.RUNNING);
        session.setStartTime(LocalDateTime.now());
        sessionDAO.insert(session);
        bestValLoss = Double.MAX_VALUE;  // reset tracker
        log.info("▶ Training session STARTED: {} ({})", session.getSessionId(), session.getModelName());
        return session.getSessionId();
    }

    /**
     * Marks a session as COMPLETED and records the end time.
     *
     * @param sessionId the UUID of the session to complete
     */
    public void completeSession(String sessionId) {
        sessionDAO.updateStatus(sessionId, TrainingSession.Status.COMPLETED, LocalDateTime.now());
        log.info("✅ Training session COMPLETED: {}", sessionId);
    }

    /**
     * Marks a session as FAILED (e.g. due to NaN loss, OOM, or exception).
     *
     * @param sessionId the UUID of the failed session
     */
    public void failSession(String sessionId) {
        sessionDAO.updateStatus(sessionId, TrainingSession.Status.FAILED, LocalDateTime.now());
        log.warn("❌ Training session FAILED: {}", sessionId);
    }

    /**
     * Marks a session as PAUSED (e.g. manual interruption or early stopping).
     *
     * @param sessionId the UUID of the paused session
     */
    public void pauseSession(String sessionId) {
        sessionDAO.updateStatus(sessionId, TrainingSession.Status.PAUSED, null);
        log.info("⏸ Training session PAUSED: {}", sessionId);
    }

    /**
     * Retrieves a session by its UUID.
     */
    public Optional<TrainingSession> getSession(String sessionId) {
        return sessionDAO.findById(sessionId);
    }

    /**
     * Returns all training sessions in the database (newest first).
     */
    public List<TrainingSession> getAllSessions() {
        return sessionDAO.findAll();
    }

    /**
     * Returns sessions filtered by status.
     */
    public List<TrainingSession> getSessionsByStatus(TrainingSession.Status status) {
        return sessionDAO.findByStatus(status);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  EPOCH LOGGING
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Logs metrics for a completed epoch.
     * Automatically checks if this is the best epoch and triggers checkpoint promotion.
     *
     * @param epoch  the TrainingEpoch with all metrics filled in
     * @return the auto-generated epoch_id
     */
    public long logEpoch(TrainingEpoch epoch) {
        if (epoch.getLoggedAt() == null) {
            epoch.setLoggedAt(LocalDateTime.now());
        }
        long id = epochDAO.insert(epoch);
        log.info("  {}", epoch);
        return id;
    }

    /**
     * Returns all logged epochs for a session.
     */
    public List<TrainingEpoch> getEpochs(String sessionId) {
        return epochDAO.findBySessionId(sessionId);
    }

    /**
     * Returns the epoch with the lowest validation loss for a session.
     */
    public Optional<TrainingEpoch> getBestEpoch(String sessionId) {
        return epochDAO.findBestEpoch(sessionId);
    }

    /**
     * Returns aggregate training metrics: [avgTrainLoss, avgValLoss, avgTrainAcc, avgValAcc]
     */
    public double[] getAggregateMetrics(String sessionId) {
        return epochDAO.getAggregateMetrics(sessionId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CHECKPOINT MANAGEMENT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Saves a model checkpoint.
     * If the checkpoint's valLoss is lower than the current best, it is automatically
     * marked as the best checkpoint.
     *
     * @param checkpoint ModelCheckpoint object
     * @return auto-generated checkpoint_id
     */
    public long saveCheckpoint(ModelCheckpoint checkpoint) {
        long id = checkpointDAO.insert(checkpoint);
        if (checkpoint.getValLoss() < bestValLoss) {
            bestValLoss = checkpoint.getValLoss();
            checkpointDAO.markAsBest(checkpoint.getSessionId(), id);
            checkpoint.setBest(true);
            log.info("  ⭐ NEW BEST checkpoint at epoch {} — valLoss={:.4f}",
                     checkpoint.getEpochNumber(), checkpoint.getValLoss());
        }
        return id;
    }

    /**
     * Returns all checkpoints for a session.
     */
    public List<ModelCheckpoint> getCheckpoints(String sessionId) {
        return checkpointDAO.findBySessionId(sessionId);
    }

    /**
     * Returns the best-marked checkpoint for a session.
     */
    public Optional<ModelCheckpoint> getBestCheckpoint(String sessionId) {
        return checkpointDAO.findBest(sessionId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  HYPERPARAMETER TRACKING
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Logs a hyperparameter change event (e.g. learning rate scheduling).
     *
     * @param sessionId   the session UUID
     * @param epochNumber the epoch at which the change happened (null = pre-training)
     * @param paramName   parameter name, e.g. "learning_rate"
     * @param oldValue    previous value as string
     * @param newValue    new value as string
     * @param reason      why the change was made
     */
    public void logHyperparameterChange(String sessionId, Integer epochNumber,
                                         String paramName, String oldValue,
                                         String newValue, String reason) {
        HyperparameterLog entry = new HyperparameterLog(
            sessionId, epochNumber, paramName, oldValue, newValue, reason);
        hparamDAO.insert(entry);
        log.info("  🔧 Hyperparameter '{}' changed: {} → {} ({})", paramName, oldValue, newValue, reason);
    }

    /**
     * Returns all hyperparameter changes for a session.
     */
    public List<HyperparameterLog> getHyperparameterChanges(String sessionId) {
        return hparamDAO.findBySessionId(sessionId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  SYSTEM METRICS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Records a system resource snapshot for the current epoch.
     *
     * @param metrics SystemMetrics object with session_id and epoch_number set
     */
    public void logSystemMetrics(SystemMetrics metrics) {
        sysMetricsDAO.insert(metrics);
    }

    /**
     * Returns all system metric snapshots for a session.
     */
    public List<SystemMetrics> getSystemMetrics(String sessionId) {
        return sysMetricsDAO.findBySessionId(sessionId);
    }

    /**
     * Returns average resource utilisation: [avgCPU, avgRAM, avgGPU, avgVRAM]
     */
    public double[] getAverageResourceUsage(String sessionId) {
        return sysMetricsDAO.getAverageUsage(sessionId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  STATISTICS
    // ═════════════════════════════════════════════════════════════════════════

    /** Returns total number of sessions in the database. */
    public int getTotalSessionCount() {
        return sessionDAO.countAll();
    }

    /** Returns total epochs logged for a session. */
    public int getEpochCount(String sessionId) {
        return epochDAO.countEpochs(sessionId);
    }
}
