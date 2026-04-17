package com.ailogger.util;

import com.ailogger.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.stream.Collectors;

/**
 * JDBC Connection utility.
 *
 * - Creates/opens the SQLite database file on first call.
 * - Runs schema.sql to ensure all tables exist (idempotent: uses IF NOT EXISTS).
 * - Provides getConnection() to callers; callers are responsible for closing.
 *
 * Usage:
 *   try (Connection conn = DBConnection.getConnection()) { ... }
 */
public class DBConnection {

    private static final Logger log = LoggerFactory.getLogger(DBConnection.class);
    private static final String DB_URL;
    private static boolean schemaInitialised = false;

    static {
        DB_URL = DatabaseConfig.getInstance().getDbUrl();
        try {
            // Explicitly load the SQLite driver (needed in some fat-jar setups)
            Class.forName(DatabaseConfig.getInstance().getDbDriver());
            log.info("SQLite JDBC driver loaded.");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError("SQLite driver not found: " + e.getMessage());
        }
    }

    /** Returns a raw JDBC connection. Always use in try-with-resources. */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        // Enable WAL mode and foreign keys for SQLite
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL;");
            st.execute("PRAGMA foreign_keys=ON;");
        }
        if (!schemaInitialised) {
            initSchema(conn);
            schemaInitialised = true;
        }
        return conn;
    }

    /**
     * Reads schema.sql from the classpath and executes each statement.
     * Safe to run multiple times because every statement uses IF NOT EXISTS.
     */
    private static synchronized void initSchema(Connection conn) {
        if (schemaInitialised) return;   // double-checked lock
        log.info("Initialising database schema...");
        try (InputStream in = DBConnection.class.getClassLoader()
                .getResourceAsStream("db/schema.sql")) {

            if (in == null) throw new RuntimeException("db/schema.sql not found!");

            String sql = new BufferedReader(new InputStreamReader(in))
                    .lines().collect(Collectors.joining("\n"));

            // Split on semicolons, strip comments, execute each statement
            String[] stmts = sql.split(";");
            try (Statement st = conn.createStatement()) {
                for (String stmt : stmts) {
                    String trimmed = stmt.trim();
                    // skip empty blocks
                    if (!trimmed.isEmpty()) {
                        st.execute(trimmed);
                    }
                }
            }
            log.info("Schema initialised successfully.");
        } catch (IOException | SQLException e) {
            throw new RuntimeException("Schema initialisation failed", e);
        }
    }

    /** Utility: quietly close a connection without throwing. */
    public static void closeQuietly(Connection conn) {
        if (conn != null) {
            try { conn.close(); } catch (SQLException ignored) {}
        }
    }
}
