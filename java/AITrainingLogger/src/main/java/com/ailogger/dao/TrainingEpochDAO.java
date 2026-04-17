package com.ailogger.dao;

import com.ailogger.model.TrainingEpoch;
import com.ailogger.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for {@link TrainingEpoch}.
 * Handles per-epoch metric persistence and retrieval.
 */
public class TrainingEpochDAO {

    private static final Logger log = LoggerFactory.getLogger(TrainingEpochDAO.class);

    // ─── INSERT ───────────────────────────────────────────────────────────────

    /**
     * Logs one epoch's metrics to the database.
     *
     * @param epoch fully populated TrainingEpoch object
     * @return the auto-generated epoch_id
     */
    public long insert(TrainingEpoch epoch) {
        String sql = """
            INSERT INTO training_epochs
              (session_id, epoch_number, train_loss, val_loss,
               train_accuracy, val_accuracy, train_f1, val_f1,
               learning_rate, epoch_duration_ms, logged_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1,  epoch.getSessionId());
            ps.setInt(2,     epoch.getEpochNumber());
            ps.setDouble(3,  epoch.getTrainLoss());
            ps.setDouble(4,  epoch.getValLoss());
            ps.setDouble(5,  epoch.getTrainAccuracy());
            ps.setDouble(6,  epoch.getValAccuracy());
            ps.setDouble(7,  epoch.getTrainF1());
            ps.setDouble(8,  epoch.getValF1());
            ps.setDouble(9,  epoch.getLearningRate());
            ps.setLong(10,   epoch.getEpochDurationMs());
            ps.setString(11, epoch.getLoggedAt() != null
                              ? epoch.getLoggedAt().toString()
                              : LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    epoch.setEpochId(id);
                    log.debug("Epoch {} logged for session {} (id={})",
                              epoch.getEpochNumber(), epoch.getSessionId(), id);
                    return id;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to insert epoch {}: {}", epoch.getEpochNumber(), e.getMessage());
            throw new RuntimeException(e);
        }
        return -1;
    }

    // ─── BATCH INSERT ─────────────────────────────────────────────────────────

    /**
     * Inserts a list of epochs in a single transaction — much faster for many epochs.
     *
     * @param epochs list of epochs to insert
     */
    public void batchInsert(List<TrainingEpoch> epochs) {
        String sql = """
            INSERT INTO training_epochs
              (session_id, epoch_number, train_loss, val_loss,
               train_accuracy, val_accuracy, train_f1, val_f1,
               learning_rate, epoch_duration_ms, logged_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (TrainingEpoch epoch : epochs) {
                    ps.setString(1,  epoch.getSessionId());
                    ps.setInt(2,     epoch.getEpochNumber());
                    ps.setDouble(3,  epoch.getTrainLoss());
                    ps.setDouble(4,  epoch.getValLoss());
                    ps.setDouble(5,  epoch.getTrainAccuracy());
                    ps.setDouble(6,  epoch.getValAccuracy());
                    ps.setDouble(7,  epoch.getTrainF1());
                    ps.setDouble(8,  epoch.getValF1());
                    ps.setDouble(9,  epoch.getLearningRate());
                    ps.setLong(10,   epoch.getEpochDurationMs());
                    ps.setString(11, LocalDateTime.now().toString());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
                log.info("Batch inserted {} epochs", epochs.size());
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("Batch insert failed: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── FIND ALL FOR SESSION ─────────────────────────────────────────────────

    /**
     * Returns all epoch records for a session, ordered by epoch number.
     *
     * @param sessionId the session UUID
     */
    public List<TrainingEpoch> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM training_epochs WHERE session_id=? ORDER BY epoch_number";
        List<TrainingEpoch> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error fetching epochs for session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException(e);
        }
        return list;
    }

    // ─── FIND BEST EPOCH ──────────────────────────────────────────────────────

    /**
     * Returns the epoch with the lowest validation loss for a session.
     * Useful to report the best model checkpoint.
     */
    public Optional<TrainingEpoch> findBestEpoch(String sessionId) {
        String sql = """
            SELECT * FROM training_epochs
            WHERE session_id=?
            ORDER BY val_loss ASC LIMIT 1
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    // ─── AGGREGATE QUERY ──────────────────────────────────────────────────────

    /**
     * Returns average train/val loss and accuracy across all epochs in a session.
     * Result array: [avgTrainLoss, avgValLoss, avgTrainAcc, avgValAcc]
     */
    public double[] getAggregateMetrics(String sessionId) {
        String sql = """
            SELECT AVG(train_loss), AVG(val_loss),
                   AVG(train_accuracy), AVG(val_accuracy)
            FROM training_epochs WHERE session_id=?
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new double[]{
                        rs.getDouble(1), rs.getDouble(2),
                        rs.getDouble(3), rs.getDouble(4)
                    };
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return new double[]{0, 0, 0, 0};
    }

    // ─── COUNT ────────────────────────────────────────────────────────────────

    /** Returns how many epoch records exist for a session. */
    public int countEpochs(String sessionId) {
        String sql = "SELECT COUNT(*) FROM training_epochs WHERE session_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ─── ROW MAPPER ───────────────────────────────────────────────────────────

    private TrainingEpoch mapRow(ResultSet rs) throws SQLException {
        TrainingEpoch e = new TrainingEpoch();
        e.setEpochId(rs.getLong("epoch_id"));
        e.setSessionId(rs.getString("session_id"));
        e.setEpochNumber(rs.getInt("epoch_number"));
        e.setTrainLoss(rs.getDouble("train_loss"));
        e.setValLoss(rs.getDouble("val_loss"));
        e.setTrainAccuracy(rs.getDouble("train_accuracy"));
        e.setValAccuracy(rs.getDouble("val_accuracy"));
        e.setTrainF1(rs.getDouble("train_f1"));
        e.setValF1(rs.getDouble("val_f1"));
        e.setLearningRate(rs.getDouble("learning_rate"));
        e.setEpochDurationMs(rs.getLong("epoch_duration_ms"));
        String loggedAt = rs.getString("logged_at");
        if (loggedAt != null) e.setLoggedAt(LocalDateTime.parse(loggedAt));
        return e;
    }
}
