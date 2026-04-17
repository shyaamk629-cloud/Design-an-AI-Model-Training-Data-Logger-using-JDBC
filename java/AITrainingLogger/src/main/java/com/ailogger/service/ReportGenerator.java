package com.ailogger.service;

import com.ailogger.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generates human-readable training reports and exports data to CSV.
 *
 * Produces:
 *  1. Console report  — printed to stdout
 *  2. Text summary    — saved to reports/<sessionId>_summary.txt
 *  3. CSV export      — saved to reports/<sessionId>_epochs.csv
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    private final TrainingLogger trainingLogger;
    private final String         reportDir;

    public ReportGenerator(TrainingLogger trainingLogger, String reportDir) {
        this.trainingLogger = trainingLogger;
        this.reportDir      = reportDir;
        try {
            Files.createDirectories(Paths.get(reportDir));
        } catch (IOException e) {
            log.warn("Could not create report directory '{}': {}", reportDir, e.getMessage());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CONSOLE REPORT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Prints a comprehensive training report to the console and saves it to disk.
     *
     * @param sessionId the session UUID to report on
     */
    public void printSessionReport(String sessionId) {
        TrainingSession session = trainingLogger.getSession(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        List<TrainingEpoch>     epochs      = trainingLogger.getEpochs(sessionId);
        List<ModelCheckpoint>   checkpoints = trainingLogger.getCheckpoints(sessionId);
        List<HyperparameterLog> hparams     = trainingLogger.getHyperparameterChanges(sessionId);
        List<SystemMetrics>     sysMetrics  = trainingLogger.getSystemMetrics(sessionId);

        double[] aggMetrics  = trainingLogger.getAggregateMetrics(sessionId);
        double[] avgResources= trainingLogger.getAverageResourceUsage(sessionId);

        StringBuilder sb = buildReport(session, epochs, checkpoints, hparams,
                                       sysMetrics, aggMetrics, avgResources);

        System.out.println(sb);
        saveReportToFile(sessionId, sb.toString());
    }

    // ─── Builder ──────────────────────────────────────────────────────────────

    private StringBuilder buildReport(
            TrainingSession session,
            List<TrainingEpoch> epochs,
            List<ModelCheckpoint> checkpoints,
            List<HyperparameterLog> hparams,
            List<SystemMetrics> sysMetrics,
            double[] aggMetrics,
            double[] avgResources) {

        StringBuilder sb = new StringBuilder();
        String line = "═".repeat(72);
        String dash = "─".repeat(72);

        // ── Header ────────────────────────────────────────────────────────────
        sb.append("\n").append(line).append("\n");
        sb.append("  🤖  AI MODEL TRAINING REPORT\n");
        sb.append(line).append("\n");
        sb.append(String.format("  Session ID   : %s%n", session.getSessionId()));
        sb.append(String.format("  Model        : %s %s%n", session.getModelName(), session.getModelVersion()));
        sb.append(String.format("  Architecture : %s%n", nvl(session.getArchitecture())));
        sb.append(String.format("  Framework    : %s%n", nvl(session.getFramework())));
        sb.append(String.format("  Dataset      : %s (%,d samples)%n",
                                session.getDatasetName(), session.getDatasetSize()));
        sb.append(dash).append("\n");

        // ── Hyperparameter Config ─────────────────────────────────────────────
        sb.append("  HYPERPARAMETERS\n");
        sb.append(String.format("    Optimizer     : %s%n", nvl(session.getOptimizer())));
        sb.append(String.format("    Learning Rate : %.6f%n", session.getLearningRate()));
        sb.append(String.format("    Batch Size    : %d%n", session.getBatchSize()));
        sb.append(String.format("    Total Epochs  : %d%n", session.getTotalEpochs()));
        sb.append(dash).append("\n");

        // ── Session Status ────────────────────────────────────────────────────
        sb.append("  RUN STATUS\n");
        sb.append(String.format("    Status     : %s%n", session.getStatus()));
        sb.append(String.format("    Started    : %s%n", session.getStartTime()));
        sb.append(String.format("    Ended      : %s%n", session.getEndTime() != null
                                ? session.getEndTime() : "still running"));
        if (session.getStartTime() != null && session.getEndTime() != null) {
            Duration dur = Duration.between(session.getStartTime(), session.getEndTime());
            sb.append(String.format("    Duration   : %02dh:%02dm:%02ds%n",
                    dur.toHours(), dur.toMinutesPart(), dur.toSecondsPart()));
        }
        sb.append(String.format("    Epochs Run : %d%n", epochs.size()));
        if (session.getNotes() != null && !session.getNotes().isBlank()) {
            sb.append(String.format("    Notes      : %s%n", session.getNotes()));
        }
        sb.append(dash).append("\n");

        // ── Aggregate Metrics ─────────────────────────────────────────────────
        sb.append("  AGGREGATE METRICS (all epochs)\n");
        sb.append(String.format("    Avg Train Loss  : %.4f%n", aggMetrics[0]));
        sb.append(String.format("    Avg Val Loss    : %.4f%n", aggMetrics[1]));
        sb.append(String.format("    Avg Train Acc   : %.2f%%%n", aggMetrics[2] * 100));
        sb.append(String.format("    Avg Val Acc     : %.2f%%%n", aggMetrics[3] * 100));
        sb.append(dash).append("\n");

        // ── Best Epoch ────────────────────────────────────────────────────────
        if (!epochs.isEmpty()) {
            epochs.stream()
                  .min((a, b) -> Double.compare(a.getValLoss(), b.getValLoss()))
                  .ifPresent(best -> {
                      sb.append("  BEST EPOCH\n");
                      sb.append(String.format("    Epoch            : %d%n", best.getEpochNumber()));
                      sb.append(String.format("    Val Loss         : %.4f%n", best.getValLoss()));
                      sb.append(String.format("    Val Accuracy     : %.2f%%%n", best.getValAccuracy() * 100));
                      sb.append(String.format("    Val F1           : %.4f%n", best.getValF1()));
                  });
            sb.append(dash).append("\n");
        }

        // ── Epoch Table ───────────────────────────────────────────────────────
        sb.append("  EPOCH-BY-EPOCH METRICS\n\n");
        sb.append(String.format("  %-6s %-10s %-10s %-11s %-11s %-8s %-8s %-8s%n",
                "Epoch", "TrainLoss", "ValLoss", "TrainAcc%", "ValAcc%",
                "TrainF1", "ValF1", "LR"));
        sb.append("  ").append("─".repeat(69)).append("\n");
        for (TrainingEpoch e : epochs) {
            sb.append(String.format("  %-6d %-10.4f %-10.4f %-11.2f %-11.2f %-8.4f %-8.4f %-8.6f%n",
                    e.getEpochNumber(),
                    e.getTrainLoss(), e.getValLoss(),
                    e.getTrainAccuracy() * 100, e.getValAccuracy() * 100,
                    e.getTrainF1(), e.getValF1(),
                    e.getLearningRate()));
        }
        sb.append(dash).append("\n");

        // ── Checkpoints ───────────────────────────────────────────────────────
        if (!checkpoints.isEmpty()) {
            sb.append("  CHECKPOINTS\n");
            for (ModelCheckpoint c : checkpoints) {
                sb.append(String.format("    Epoch %-3d | valLoss=%.4f | valAcc=%.2f%% | best=%-5s | %s%n",
                        c.getEpochNumber(), c.getValLoss(),
                        c.getValAccuracy() * 100,
                        c.isBest() ? "✓ YES" : "no",
                        c.getFilePath()));
            }
            sb.append(dash).append("\n");
        }

        // ── Hyperparameter Changes ────────────────────────────────────────────
        if (!hparams.isEmpty()) {
            sb.append("  HYPERPARAMETER CHANGES\n");
            for (HyperparameterLog h : hparams) {
                sb.append(String.format("    Epoch %-3s | %-20s : %-12s → %s [%s]%n",
                        h.getEpochNumber() != null ? h.getEpochNumber() : "pre",
                        h.getParamName(), h.getOldValue(),
                        h.getNewValue(), nvl(h.getReason())));
            }
            sb.append(dash).append("\n");
        }

        // ── System Metrics ────────────────────────────────────────────────────
        if (avgResources[0] > 0 || avgResources[1] > 0) {
            sb.append("  AVERAGE RESOURCE UTILIZATION\n");
            sb.append(String.format("    CPU           : %.1f%%%n", avgResources[0]));
            sb.append(String.format("    RAM           : %.0f MB%n", avgResources[1]));
            sb.append(String.format("    GPU           : %.1f%%%n", avgResources[2]));
            sb.append(String.format("    GPU Memory    : %.0f MB%n", avgResources[3]));
            sb.append(dash).append("\n");
        }

        sb.append("\n  Report generated: ").append(LocalDateTime.now()).append("\n");
        sb.append(line).append("\n");
        return sb;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CSV EXPORT
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Exports all epoch metrics for a session to a CSV file.
     *
     * @param sessionId the session UUID
     * @return the file path of the exported CSV
     */
    public String exportEpochsToCSV(String sessionId) {
        String filePath = reportDir + sessionId.substring(0, 8) + "_epochs.csv";
        List<TrainingEpoch> epochs = trainingLogger.getEpochs(sessionId);

        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            // Header row
            pw.println("epoch_number,train_loss,val_loss,train_accuracy,val_accuracy," +
                       "train_f1,val_f1,learning_rate,epoch_duration_ms,logged_at");
            // Data rows
            for (TrainingEpoch e : epochs) {
                pw.printf("%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.8f,%d,%s%n",
                        e.getEpochNumber(),
                        e.getTrainLoss(), e.getValLoss(),
                        e.getTrainAccuracy(), e.getValAccuracy(),
                        e.getTrainF1(), e.getValF1(),
                        e.getLearningRate(), e.getEpochDurationMs(),
                        e.getLoggedAt());
            }
            log.info("CSV exported: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to export CSV: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return filePath;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  OVERVIEW TABLE (all sessions)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Prints a summary table of all sessions — useful for comparing runs.
     */
    public void printAllSessionsOverview() {
        List<TrainingSession> sessions = trainingLogger.getAllSessions();
        System.out.println("\n" + "═".repeat(90));
        System.out.println("  ALL TRAINING SESSIONS OVERVIEW");
        System.out.println("═".repeat(90));
        System.out.printf("  %-8s %-15s %-8s %-10s %-8s %-10s %-10s%n",
                "ID(8)", "Model", "Version", "Dataset", "Status", "LR", "Epochs");
        System.out.println("  " + "─".repeat(78));

        for (TrainingSession s : sessions) {
            System.out.printf("  %-8s %-15s %-8s %-10s %-8s %-10.6f %-10d%n",
                    s.getSessionId().substring(0, 8),
                    s.getModelName(), s.getModelVersion(),
                    s.getDatasetName(), s.getStatus(),
                    s.getLearningRate(), s.getTotalEpochs());
        }
        System.out.printf("%n  Total sessions: %d%n", sessions.size());
        System.out.println("═".repeat(90) + "\n");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void saveReportToFile(String sessionId, String content) {
        String filePath = reportDir + sessionId.substring(0, 8) + "_summary.txt";
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath))) {
            pw.print(content);
            log.info("Report saved to: {}", filePath);
        } catch (IOException e) {
            log.warn("Could not save report to disk: {}", e.getMessage());
        }
    }

    private String nvl(String s) { return s != null ? s : "N/A"; }
}
