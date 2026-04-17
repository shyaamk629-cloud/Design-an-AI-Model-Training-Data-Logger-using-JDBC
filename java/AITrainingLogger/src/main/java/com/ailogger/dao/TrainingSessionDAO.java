package com.ailogger.dao;

import com.ailogger.model.TrainingSession;
import com.ailogger.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object for {@link TrainingSession}.
 *
 * Provides full CRUD + query operations using raw JDBC PreparedStatements.
 * Every method opens its own connection and closes it via try-with-resources.
 */
public class TrainingSessionDAO {

    private static final Logger log = LoggerFactory.getLogger(TrainingSessionDAO.class);

    // ─── INSERT ───────────────────────────────────────────────────────────────

    /**
     * Persists a new training session to the database.
     *
     * @param session the session to insert (session_id must already be set)
     * @return true if the row was inserted successfully
     */
    public boolean insert(TrainingSession session) {
        String sql = """
            INSERT INTO training_sessions
              (session_id, model_name, model_version, dataset_name, dataset_size,
               architecture, framework, optimizer, learning_rate, batch_size,
               total_epochs, status, start_time, end_time, notes)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1,  session.getSessionId());
            ps.setString(2,  session.getModelName());
            ps.setString(3,  session.getModelVersion());
            ps.setString(4,  session.getDatasetName());
            ps.setInt(5,     session.getDatasetSize());
            ps.setString(6,  session.getArchitecture());
            ps.setString(7,  session.getFramework());
            ps.setString(8,  session.getOptimizer());
            ps.setDouble(9,  session.getLearningRate());
            ps.setInt(10,    session.getBatchSize());
            ps.setInt(11,    session.getTotalEpochs());
            ps.setString(12, session.getStatus().name());
            ps.setString(13, session.getStartTime() != null ? session.getStartTime().toString() : null);
            ps.setString(14, session.getEndTime()   != null ? session.getEndTime().toString()   : null);
            ps.setString(15, session.getNotes());

            int rows = ps.executeUpdate();
            log.info("Session inserted: {}", session.getSessionId());
            return rows == 1;

        } catch (SQLException e) {
            log.error("Failed to insert session {}: {}", session.getSessionId(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── FIND BY ID ───────────────────────────────────────────────────────────

    /**
     * Retrieves a session by its UUID.
     *
     * @param sessionId the UUID string
     * @return Optional containing the session if found
     */
    public Optional<TrainingSession> findById(String sessionId) {
        String sql = "SELECT * FROM training_sessions WHERE session_id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Error finding session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException(e);
        }
        return Optional.empty();
    }

    // ─── FIND ALL ─────────────────────────────────────────────────────────────

    /** Returns all sessions ordered by start time (most recent first). */
    public List<TrainingSession> findAll() {
        String sql = "SELECT * FROM training_sessions ORDER BY start_time DESC";
        List<TrainingSession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            log.error("Error fetching all sessions: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return list;
    }

    // ─── FIND BY STATUS ───────────────────────────────────────────────────────

    /** Returns sessions filtered by status (RUNNING, COMPLETED, FAILED, PAUSED). */
    public List<TrainingSession> findByStatus(TrainingSession.Status status) {
        String sql = "SELECT * FROM training_sessions WHERE status = ? ORDER BY start_time DESC";
        List<TrainingSession> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.error("Error querying sessions by status: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return list;
    }

    // ─── UPDATE STATUS ────────────────────────────────────────────────────────

    /**
     * Updates the status and end time of a session.
     * Call this when training completes, fails, or is paused.
     *
     * @param sessionId the session UUID
     * @param status    new status
     * @param endTime   completion/failure time (null to keep existing)
     */
    public boolean updateStatus(String sessionId, TrainingSession.Status status,
                                 LocalDateTime endTime) {
        String sql = "UPDATE training_sessions SET status=?, end_time=? WHERE session_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status.name());
            ps.setString(2, endTime != null ? endTime.toString() : null);
            ps.setString(3, sessionId);

            int rows = ps.executeUpdate();
            log.info("Session {} status updated to {}", sessionId, status);
            return rows == 1;

        } catch (SQLException e) {
            log.error("Failed to update session status: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    /** Deletes a session and all child records (cascade). */
    public boolean delete(String sessionId) {
        String sql = "DELETE FROM training_sessions WHERE session_id=?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, sessionId);
            return ps.executeUpdate() == 1;

        } catch (SQLException e) {
            log.error("Failed to delete session {}: {}", sessionId, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ─── COUNT ────────────────────────────────────────────────────────────────

    /** Returns the total number of training sessions stored. */
    public int countAll() {
        try (Connection conn = DBConnection.getConnection();
             Statement st   = conn.createStatement();
             ResultSet rs   = st.executeQuery("SELECT COUNT(*) FROM training_sessions")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ─── ROW MAPPER ───────────────────────────────────────────────────────────

    /** Maps a ResultSet row to a {@link TrainingSession} object. */
    private TrainingSession mapRow(ResultSet rs) throws SQLException {
        TrainingSession s = new TrainingSession();
        s.setSessionId(rs.getString("session_id"));
        s.setModelName(rs.getString("model_name"));
        s.setModelVersion(rs.getString("model_version"));
        s.setDatasetName(rs.getString("dataset_name"));
        s.setDatasetSize(rs.getInt("dataset_size"));
        s.setArchitecture(rs.getString("architecture"));
        s.setFramework(rs.getString("framework"));
        s.setOptimizer(rs.getString("optimizer"));
        s.setLearningRate(rs.getDouble("learning_rate"));
        s.setBatchSize(rs.getInt("batch_size"));
        s.setTotalEpochs(rs.getInt("total_epochs"));
        s.setStatus(TrainingSession.Status.valueOf(rs.getString("status")));
        String start = rs.getString("start_time");
        String end   = rs.getString("end_time");
        if (start != null) s.setStartTime(LocalDateTime.parse(start));
        if (end   != null) s.setEndTime(LocalDateTime.parse(end));
        s.setNotes(rs.getString("notes"));
        return s;
    }
}
