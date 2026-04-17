package com.ailogger.dao;

import com.ailogger.model.ModelCheckpoint;
import com.ailogger.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for {@link ModelCheckpoint}.
 */
public class ModelCheckpointDAO {

    private static final Logger log = LoggerFactory.getLogger(ModelCheckpointDAO.class);

    // ─── INSERT ───────────────────────────────────────────────────────────────

    public long insert(ModelCheckpoint ckpt) {
        String sql = """
            INSERT INTO model_checkpoints
              (session_id, epoch_number, file_path, val_loss, val_accuracy, is_best, saved_at)
            VALUES (?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, ckpt.getSessionId());
            ps.setInt(2,    ckpt.getEpochNumber());
            ps.setString(3, ckpt.getFilePath());
            ps.setDouble(4, ckpt.getValLoss());
            ps.setDouble(5, ckpt.getValAccuracy());
            ps.setInt(6,    ckpt.isBest() ? 1 : 0);
            ps.setString(7, ckpt.getSavedAt() != null
                            ? ckpt.getSavedAt().toString()
                            : LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    ckpt.setCheckpointId(id);
                    log.info("Checkpoint saved at epoch {} (id={})", ckpt.getEpochNumber(), id);
                    return id;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to save checkpoint: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return -1;
    }

    // ─── MARK BEST ────────────────────────────────────────────────────────────

    /**
     * Resets all checkpoints for a session to is_best=0, then marks the given
     * checkpoint as best. Call when a new best validation loss is achieved.
     *
     * @param sessionId    the session UUID
     * @param checkpointId the checkpoint to promote to "best"
     */
    public void markAsBest(String sessionId, long checkpointId) {
        String clearSql = "UPDATE model_checkpoints SET is_best=0 WHERE session_id=?";
        String markSql  = "UPDATE model_checkpoints SET is_best=1 WHERE checkpoint_id=?";
        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement clear = conn.prepareStatement(clearSql);
                 PreparedStatement mark  = conn.prepareStatement(markSql)) {
                clear.setString(1, sessionId);
                clear.executeUpdate();
                mark.setLong(1, checkpointId);
                mark.executeUpdate();
                conn.commit();
                log.info("Checkpoint {} marked as best for session {}", checkpointId, sessionId);
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ─── FIND BEST ────────────────────────────────────────────────────────────

    /** Returns the best checkpoint for a session. */
    public Optional<ModelCheckpoint> findBest(String sessionId) {
        String sql = "SELECT * FROM model_checkpoints WHERE session_id=? AND is_best=1 LIMIT 1";
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

    // ─── FIND ALL FOR SESSION ─────────────────────────────────────────────────

    public List<ModelCheckpoint> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM model_checkpoints WHERE session_id=? ORDER BY epoch_number";
        List<ModelCheckpoint> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    // ─── ROW MAPPER ───────────────────────────────────────────────────────────

    private ModelCheckpoint mapRow(ResultSet rs) throws SQLException {
        ModelCheckpoint c = new ModelCheckpoint();
        c.setCheckpointId(rs.getLong("checkpoint_id"));
        c.setSessionId(rs.getString("session_id"));
        c.setEpochNumber(rs.getInt("epoch_number"));
        c.setFilePath(rs.getString("file_path"));
        c.setValLoss(rs.getDouble("val_loss"));
        c.setValAccuracy(rs.getDouble("val_accuracy"));
        c.setBest(rs.getInt("is_best") == 1);
        String savedAt = rs.getString("saved_at");
        if (savedAt != null) c.setSavedAt(LocalDateTime.parse(savedAt));
        return c;
    }
}
