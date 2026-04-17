package com.ailogger.dao;

import com.ailogger.model.HyperparameterLog;
import com.ailogger.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link HyperparameterLog}.
 */
public class HyperparameterLogDAO {

    private static final Logger log = LoggerFactory.getLogger(HyperparameterLogDAO.class);

    // ─── INSERT ───────────────────────────────────────────────────────────────

    public long insert(HyperparameterLog entry) {
        String sql = """
            INSERT INTO hyperparameter_log
              (session_id, epoch_number, param_name, old_value, new_value, reason, changed_at)
            VALUES (?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, entry.getSessionId());
            if (entry.getEpochNumber() != null) ps.setInt(2, entry.getEpochNumber());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, entry.getParamName());
            ps.setString(4, entry.getOldValue());
            ps.setString(5, entry.getNewValue());
            ps.setString(6, entry.getReason());
            ps.setString(7, entry.getChangedAt() != null
                            ? entry.getChangedAt().toString()
                            : LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    entry.setLogId(id);
                    log.info("Hyperparameter change logged: {} -> {}", entry.getParamName(), entry.getNewValue());
                    return id;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to log hyperparameter change: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return -1;
    }

    // ─── FIND BY SESSION ──────────────────────────────────────────────────────

    public List<HyperparameterLog> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM hyperparameter_log WHERE session_id=? ORDER BY changed_at";
        List<HyperparameterLog> list = new ArrayList<>();
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

    // ─── FIND BY PARAM NAME ───────────────────────────────────────────────────

    public List<HyperparameterLog> findByParam(String sessionId, String paramName) {
        String sql = "SELECT * FROM hyperparameter_log WHERE session_id=? AND param_name=? ORDER BY changed_at";
        List<HyperparameterLog> list = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, paramName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return list;
    }

    // ─── ROW MAPPER ───────────────────────────────────────────────────────────

    private HyperparameterLog mapRow(ResultSet rs) throws SQLException {
        HyperparameterLog h = new HyperparameterLog();
        h.setLogId(rs.getLong("log_id"));
        h.setSessionId(rs.getString("session_id"));
        int epoch = rs.getInt("epoch_number");
        h.setEpochNumber(rs.wasNull() ? null : epoch);
        h.setParamName(rs.getString("param_name"));
        h.setOldValue(rs.getString("old_value"));
        h.setNewValue(rs.getString("new_value"));
        h.setReason(rs.getString("reason"));
        String changedAt = rs.getString("changed_at");
        if (changedAt != null) h.setChangedAt(LocalDateTime.parse(changedAt));
        return h;
    }
}
