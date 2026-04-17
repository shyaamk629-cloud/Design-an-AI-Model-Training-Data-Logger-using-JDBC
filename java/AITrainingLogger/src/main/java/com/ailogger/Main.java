package com.ailogger;

import com.ailogger.config.DatabaseConfig;
import com.ailogger.model.*;
import com.ailogger.service.ReportGenerator;
import com.ailogger.service.TrainingLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

/**
 * ╔══════════════════════════════════════════════════════════════════╗
 * ║        AI MODEL TRAINING DATA LOGGER — DEMO APPLICATION         ║
 * ║                  Implemented with JDBC + SQLite                  ║
 * ╚══════════════════════════════════════════════════════════════════╝
 *
 * This demo simulates two complete training runs:
 *   1. A CNN image classifier (ResNet-like) trained for 20 epochs
 *   2. A Transformer text classifier trained for 15 epochs
 *
 * It demonstrates:
 *   • Starting / completing training sessions
 *   • Logging per-epoch metrics (loss, accuracy, F1, duration)
 *   • Saving model checkpoints (best-model tracking)
 *   • Logging hyperparameter changes (LR scheduling)
 *   • Recording system resource metrics
 *   • Generating full session reports + CSV export
 *   • Querying all sessions overview
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final Random rng = new Random(42);

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║    AI Model Training Data Logger — JDBC Demo             ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝\n");

        // ── Bootstrap services ────────────────────────────────────────────────
        TrainingLogger  logger    = new TrainingLogger();
        ReportGenerator reporter  = new ReportGenerator(
                logger, DatabaseConfig.getInstance().getReportOutputDir());

        // ── Demo Run 1: CNN Image Classifier ─────────────────────────────────
        String session1Id = runCNNDemo(logger);

        // ── Demo Run 2: Transformer Text Classifier ───────────────────────────
        String session2Id = runTransformerDemo(logger);

        // ── Reports ───────────────────────────────────────────────────────────
        System.out.println("\n\n📊 Generating reports...\n");
        reporter.printAllSessionsOverview();
        reporter.printSessionReport(session1Id);

        String csvPath = reporter.exportEpochsToCSV(session1Id);
        System.out.println("\n📁 CSV exported to: " + csvPath);

        reporter.printSessionReport(session2Id);

        System.out.println("\n✅ Demo complete! Database: aitraining_log.db");
        System.out.println("   Reports saved in: " + DatabaseConfig.getInstance().getReportOutputDir());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DEMO 1 — CNN Image Classifier
    // ═════════════════════════════════════════════════════════════════════════

    private static String runCNNDemo(TrainingLogger logger) {
        System.out.println("▶ Starting Demo 1: CNN Image Classifier (ResNet-34)");
        System.out.println("─".repeat(60));

        // ── Build session ─────────────────────────────────────────────────────
        TrainingSession session = new TrainingSession();
        session.setModelName("ResNet-34");
        session.setModelVersion("v1.2.0");
        session.setDatasetName("CIFAR-10");
        session.setDatasetSize(50_000);
        session.setArchitecture("Residual CNN");
        session.setFramework("PyTorch");
        session.setOptimizer("Adam");
        session.setLearningRate(0.001);
        session.setBatchSize(64);
        session.setTotalEpochs(20);
        session.setNotes("Baseline CNN run with cosine LR decay");

        String sessionId = logger.startSession(session);

        // Simulate training for 20 epochs
        double lr         = 0.001;
        double trainLoss  = 2.5;
        double valLoss    = 2.7;
        double bestValLos = Double.MAX_VALUE;

        for (int epoch = 1; epoch <= 20; epoch++) {

            long epochStart = System.currentTimeMillis();

            // Simulate gradual improvement with some noise
            trainLoss -= (0.08 + rng.nextDouble() * 0.05);
            valLoss   -= (0.06 + rng.nextDouble() * 0.04);
            trainLoss  = Math.max(trainLoss, 0.05);
            valLoss    = Math.max(valLoss,   0.08);

            double trainAcc = Math.min(0.99, 1.0 - (trainLoss / 3.0) + rng.nextDouble() * 0.03);
            double valAcc   = Math.min(0.97, 1.0 - (valLoss   / 3.0) + rng.nextDouble() * 0.03);
            double trainF1  = trainAcc * (0.95 + rng.nextDouble() * 0.05);
            double valF1    = valAcc   * (0.95 + rng.nextDouble() * 0.05);

            long durationMs = 800 + (long)(rng.nextDouble() * 400);

            // ── Log epoch ────────────────────────────────────────────────────
            TrainingEpoch ep = new TrainingEpoch(sessionId, epoch,
                    trainLoss, valLoss, trainAcc, valAcc);
            ep.setTrainF1(trainF1);
            ep.setValF1(valF1);
            ep.setLearningRate(lr);
            ep.setEpochDurationMs(durationMs);
            logger.logEpoch(ep);

            // ── Save checkpoint at every 5th epoch ───────────────────────────
            if (epoch % 5 == 0 || valLoss < bestValLos) {
                ModelCheckpoint ckpt = new ModelCheckpoint(
                        sessionId, epoch,
                        "checkpoints/resnet34_ep" + epoch + ".pt",
                        valLoss, valAcc);
                logger.saveCheckpoint(ckpt);
                bestValLos = Math.min(bestValLos, valLoss);
            }

            // ── Simulate LR decay at epoch 10 ────────────────────────────────
            if (epoch == 10) {
                double oldLr = lr;
                lr *= 0.1;  // reduce LR by 10x
                logger.logHyperparameterChange(
                        sessionId, epoch,
                        "learning_rate",
                        String.valueOf(oldLr), String.valueOf(lr),
                        "ReduceLROnPlateau — val_loss plateaued");
            }

            // ── Log system metrics every 5 epochs ────────────────────────────
            if (epoch % 5 == 0) {
                SystemMetrics sysM = new SystemMetrics(
                        sessionId, epoch,
                        45.0 + rng.nextDouble() * 30,   // CPU %
                        3200 + rng.nextDouble() * 800,   // RAM MB
                        85.0 + rng.nextDouble() * 10,    // GPU %
                        4000 + rng.nextDouble() * 1000); // VRAM MB
                logger.logSystemMetrics(sysM);
            }

            // Cosine LR decay simulation
            lr = 0.001 * 0.5 * (1 + Math.cos(Math.PI * epoch / 20));
        }

        logger.completeSession(sessionId);
        System.out.println("─".repeat(60));
        System.out.printf("✅ Demo 1 complete. Session ID: %s%n%n", sessionId);
        return sessionId;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  DEMO 2 — Transformer Text Classifier
    // ═════════════════════════════════════════════════════════════════════════

    private static String runTransformerDemo(TrainingLogger logger) {
        System.out.println("▶ Starting Demo 2: Transformer Text Classifier (BERT-base)");
        System.out.println("─".repeat(60));

        TrainingSession session = new TrainingSession();
        session.setModelName("BERT-Base");
        session.setModelVersion("v2.0.1");
        session.setDatasetName("SST-2");
        session.setDatasetSize(67_349);
        session.setArchitecture("Transformer Encoder");
        session.setFramework("HuggingFace + PyTorch");
        session.setOptimizer("AdamW");
        session.setLearningRate(2e-5);
        session.setBatchSize(32);
        session.setTotalEpochs(15);
        session.setNotes("Fine-tuning BERT on sentiment classification");

        String sessionId = logger.startSession(session);

        double lr        = 2e-5;
        double trainLoss = 1.8;
        double valLoss   = 2.0;
        double bestVal   = Double.MAX_VALUE;

        for (int epoch = 1; epoch <= 15; epoch++) {

            // Transformer converges faster
            trainLoss -= (0.09 + rng.nextDouble() * 0.06);
            valLoss   -= (0.07 + rng.nextDouble() * 0.05);
            trainLoss  = Math.max(trainLoss, 0.03);
            valLoss    = Math.max(valLoss,   0.06);

            double trainAcc = Math.min(0.995, 1.0 - (trainLoss / 2.5) + rng.nextDouble() * 0.02);
            double valAcc   = Math.min(0.980, 1.0 - (valLoss   / 2.5) + rng.nextDouble() * 0.02);
            double trainF1  = trainAcc * (0.97 + rng.nextDouble() * 0.03);
            double valF1    = valAcc   * (0.97 + rng.nextDouble() * 0.03);
            long   duration = 2000 + (long)(rng.nextDouble() * 1000);

            TrainingEpoch ep = new TrainingEpoch(sessionId, epoch,
                    trainLoss, valLoss, trainAcc, valAcc);
            ep.setTrainF1(trainF1);
            ep.setValF1(valF1);
            ep.setLearningRate(lr);
            ep.setEpochDurationMs(duration);
            logger.logEpoch(ep);

            // Save checkpoint whenever val improves
            if (valLoss < bestVal) {
                ModelCheckpoint ckpt = new ModelCheckpoint(
                        sessionId, epoch,
                        "checkpoints/bert_base_ep" + epoch + ".bin",
                        valLoss, valAcc);
                logger.saveCheckpoint(ckpt);
                bestVal = valLoss;
            }

            // Warmup → linear decay LR schedule
            if (epoch == 5) {
                double oldLr = lr;
                lr = 1e-5;
                logger.logHyperparameterChange(sessionId, epoch,
                        "learning_rate",
                        String.format("%.2e", oldLr),
                        String.format("%.2e", lr),
                        "Linear LR decay starts after warmup");
            }

            SystemMetrics sysM = new SystemMetrics(
                    sessionId, epoch,
                    55.0 + rng.nextDouble() * 25,
                    8000 + rng.nextDouble() * 2000,
                    90.0 + rng.nextDouble() * 8,
                    10000 + rng.nextDouble() * 2000);
            logger.logSystemMetrics(sysM);

            // Simulate linear LR decay
            lr = 2e-5 * (1.0 - (double) epoch / 15);
            lr = Math.max(lr, 0);
        }

        // Simulate early stopping
        logger.logHyperparameterChange(sessionId, 15,
                "early_stopping", "false", "true",
                "Val loss not improving for 3 epochs — stopping");
        logger.completeSession(sessionId);

        System.out.println("─".repeat(60));
        System.out.printf("✅ Demo 2 complete. Session ID: %s%n%n", sessionId);
        return sessionId;
    }
}
