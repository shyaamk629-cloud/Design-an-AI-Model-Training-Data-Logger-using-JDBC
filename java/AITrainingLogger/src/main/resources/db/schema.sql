-- ============================================================
--  AI Model Training Data Logger - Database Schema (SQLite)
-- ============================================================

-- ► training_sessions: tracks each unique model training run
CREATE TABLE IF NOT EXISTS training_sessions (
    session_id      TEXT PRIMARY KEY,          -- UUID
    model_name      TEXT NOT NULL,             -- e.g. "ResNet-50, GPT-2"
    model_version   TEXT NOT NULL,             -- e.g. "v1.0.3"
    dataset_name    TEXT NOT NULL,             -- dataset used for training
    dataset_size    INTEGER NOT NULL,          -- number of samples
    architecture    TEXT,                      -- model architecture description
    framework       TEXT,                      -- TensorFlow, PyTorch, etc.
    optimizer       TEXT,                      -- Adam, SGD, RMSprop
    learning_rate   REAL,                      -- initial learning rate
    batch_size      INTEGER,                   -- mini-batch size
    total_epochs    INTEGER,                   -- planned number of epochs
    status          TEXT NOT NULL              -- RUNNING | COMPLETED | FAILED | PAUSED
                        CHECK(status IN ('RUNNING','COMPLETED','FAILED','PAUSED')),
    start_time      TEXT NOT NULL,             -- ISO-8601 datetime
    end_time        TEXT,                      -- NULL while running
    notes           TEXT                       -- free-form notes
);

-- ► training_epochs: per-epoch metrics for each session
CREATE TABLE IF NOT EXISTS training_epochs (
    epoch_id        INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    epoch_number    INTEGER NOT NULL,
    train_loss      REAL NOT NULL,
    val_loss        REAL,
    train_accuracy  REAL,
    val_accuracy    REAL,
    train_f1        REAL,                      -- F1 score on training set
    val_f1          REAL,                      -- F1 score on validation set
    learning_rate   REAL,                      -- LR at this epoch (may decay)
    epoch_duration_ms BIGINT,                  -- wall-clock ms for this epoch
    logged_at       TEXT NOT NULL,             -- ISO-8601 datetime
    FOREIGN KEY (session_id) REFERENCES training_sessions(session_id),
    UNIQUE (session_id, epoch_number)
);

-- ► model_checkpoints: saved model states
CREATE TABLE IF NOT EXISTS model_checkpoints (
    checkpoint_id   INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    epoch_number    INTEGER NOT NULL,
    file_path       TEXT NOT NULL,             -- path to saved weights
    val_loss        REAL,
    val_accuracy    REAL,
    is_best         INTEGER DEFAULT 0          -- 1 if best checkpoint so far
                        CHECK(is_best IN (0,1)),
    saved_at        TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES training_sessions(session_id)
);

-- ► hyperparameter_log: tracks any mid-training hyperparameter changes
CREATE TABLE IF NOT EXISTS hyperparameter_log (
    log_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    epoch_number    INTEGER,
    param_name      TEXT NOT NULL,             -- e.g. "learning_rate"
    old_value       TEXT,
    new_value       TEXT NOT NULL,
    reason          TEXT,                      -- why was it changed
    changed_at      TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES training_sessions(session_id)
);

-- ► system_metrics: hardware/resource metrics per epoch
CREATE TABLE IF NOT EXISTS system_metrics (
    metric_id       INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id      TEXT NOT NULL,
    epoch_number    INTEGER,
    cpu_usage_pct   REAL,
    ram_used_mb     REAL,
    gpu_usage_pct   REAL,
    gpu_memory_mb   REAL,
    recorded_at     TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES training_sessions(session_id)
);

-- Indexes for fast querying
CREATE INDEX IF NOT EXISTS idx_epochs_session   ON training_epochs(session_id);
CREATE INDEX IF NOT EXISTS idx_ckpt_session     ON model_checkpoints(session_id);
CREATE INDEX IF NOT EXISTS idx_hparam_session   ON hyperparameter_log(session_id);
CREATE INDEX IF NOT EXISTS idx_sysmet_session   ON system_metrics(session_id);
