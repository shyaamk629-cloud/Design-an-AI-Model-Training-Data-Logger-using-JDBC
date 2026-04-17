package com.ailogger.dao;

import com.ailogger.model.SystemMetrics;
import com.ailogger.util.DBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for {@link SystemMetrics}.
 */
public class SystemMetricsDAO {

    private static final Logger log = LoggerFactory.getLogger(SystemMetricsDAO.class);

    // ─── INSERT ───────────────────────────────────────────────────────────────

    public long insert(SystemMetrics m) {
        String sql = """
            INSERT INTO system_metrics
              (session_id, epoch_number, cpu_usage_pct, ram_used_mb,
               gpu_usage_pct, gpu_memory_mb, recorded_at)
            VALUES (?,?,?,?,?,?,?)
            """;
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, m.getSessionId());
            if (m.getEpochNumber() != null) ps.setInt(2, m.getEpochNumber());
            else ps.setNull(2, Types.INTEGER);
            ps.setDouble(3, m.getCpuUsagePct());
            ps.setDouble(4, m.getRamUsedMb());
            ps.setDouble(5, m.getGpuUsagePct());
            ps.setDouble(6, m.getGpuMemoryMb());
            ps.setString(7, m.getRecordedAt() != null
                            ? m.getRecordedAt().toString()
                            : LocalDateTime.now().toString());

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    m.setMetricId(id);
                    return id;
                }
            }
        } catch (SQLException e) {
            log.error("Failed to record system metrics: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        return -1;
    }

    // ─── FIND BY SESSION ──────────────────────────────────────────────────────

    public List<SystemMetrics> findBySessionId(String sessionId) {
        String sql = "SELECT * FROM system_metrics WHERE session_id=? ORDER BY recorded_at";
        List<SystemMetrics> list = new ArrayList<>();
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

    // ─── AVERAGE RESOURCE USAGE ───────────────────────────────────────────────

    /**
     * Returns [avgCPU, avgRAM, avgGPU, avgVRAM] for the session.
     */
    public double[] getAverageUsage(String sessionId) {
        String sql = """
            SELECT AVG(cpu_usage_pct), AVG(ram_used_mb),
                   AVG(gpu_usage_pct), AVG(gpu_memory_mb)
            FROM system_metrics WHERE session_id=?
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

    // ─── ROW MAPPER ───────────────────────────────────────────────────────────

    private SystemMetrics mapRow(ResultSet rs) throws SQLException {
        SystemMetrics m = new SystemMetrics();
        m.setMetricId(rs.getLong("metric_id"));
        m.setSessionId(rs.getString("session_id"));
        int epoch = rs.getInt("epoch_number");
        m.setEpochNumber(rs.wasNull() ? null : epoch);
        m.setCpuUsagePct(rs.getDouble("cpu_usage_pct"));
        m.setRamUsedMb(rs.getDouble("ram_used_mb"));
        m.setGpuUsagePct(rs.getDouble("gpu_usage_pct"));
        m.setGpuMemoryMb(rs.getDouble("gpu_memory_mb"));
        String recordedAt = rs.getString("recorded_at");
        if (recordedAt != null) m.setRecordedAt(LocalDateTime.parse(recordedAt));
        return m;
    }
}
