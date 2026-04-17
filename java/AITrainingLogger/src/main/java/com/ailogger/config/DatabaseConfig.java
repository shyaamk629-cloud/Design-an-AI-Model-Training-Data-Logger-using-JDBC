package com.ailogger.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads application configuration from config.properties at startup.
 * Singleton pattern — call DatabaseConfig.getInstance() everywhere.
 */
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);
    private static DatabaseConfig instance;

    private final Properties props = new Properties();

    private DatabaseConfig() {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in == null) {
                throw new RuntimeException("config.properties not found on classpath!");
            }
            props.load(in);
            log.info("Configuration loaded successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    /** Returns the singleton instance (thread-safe). */
    public static synchronized DatabaseConfig getInstance() {
        if (instance == null) {
            instance = new DatabaseConfig();
        }
        return instance;
    }

    public String getDbUrl()          { return props.getProperty("db.url"); }
    public String getDbDriver()       { return props.getProperty("db.driver"); }
    public String getReportOutputDir(){ return props.getProperty("report.output.dir", "reports/"); }
    public String getAppName()        { return props.getProperty("logger.app.name", "AITrainingLogger"); }
}
