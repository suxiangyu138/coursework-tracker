-- 作业哨兵 数据库结构 v1
-- 时间统一存本地时间字符串 yyyy-MM-dd HH:mm:ss（字典序 = 时间序）
-- 布尔用 INTEGER 0/1，权重为百分数 0–100

CREATE TABLE IF NOT EXISTS course (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL UNIQUE,
    code         TEXT,
    credit       REAL    NOT NULL DEFAULT 0 CHECK (credit >= 0 AND credit <= 20),
    teacher      TEXT,
    semester     TEXT,
    target_score REAL             CHECK (target_score IS NULL OR (target_score > 0 AND target_score <= 100)),
    archived     INTEGER NOT NULL DEFAULT 0 CHECK (archived IN (0, 1)),
    created_at   TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS assignment (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id         INTEGER NOT NULL REFERENCES course(id) ON DELETE CASCADE,
    title             TEXT    NOT NULL,
    due_at            TEXT    NOT NULL,
    weight            REAL    NOT NULL DEFAULT 0 CHECK (weight >= 0 AND weight <= 100),
    estimated_minutes INTEGER          CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
    status            TEXT    NOT NULL DEFAULT 'TODO'
                              CHECK (status IN ('TODO', 'DOING', 'DONE', 'SUBMITTED')),
    submitted_at      TEXT,
    score             REAL             CHECK (score IS NULL OR (score >= 0 AND score <= 100)),
    notes             TEXT,
    created_at        TEXT    NOT NULL,
    updated_at        TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS reminder_log (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    assignment_id INTEGER NOT NULL REFERENCES assignment(id) ON DELETE CASCADE,
    rule_code     TEXT    NOT NULL,
    fired_date    TEXT    NOT NULL,
    fired_at      TEXT    NOT NULL,
    UNIQUE (assignment_id, rule_code, fired_date)
);

CREATE TABLE IF NOT EXISTS setting (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_assignment_due    ON assignment(due_at);
CREATE INDEX IF NOT EXISTS idx_assignment_course ON assignment(course_id);
CREATE INDEX IF NOT EXISTS idx_assignment_status ON assignment(status);
CREATE INDEX IF NOT EXISTS idx_course_semester   ON course(semester);
CREATE INDEX IF NOT EXISTS idx_reminder_fired    ON reminder_log(fired_at);

CREATE TABLE IF NOT EXISTS ai_conversation (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    title      TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_message (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id INTEGER NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    role            TEXT    NOT NULL CHECK (role IN ('user', 'assistant', 'tool')),
    content         TEXT,
    tool_calls      TEXT,
    tool_call_id    TEXT,
    created_at      TEXT    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_message_conv ON ai_message(conversation_id, id);
