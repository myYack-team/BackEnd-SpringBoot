-- Add monthly quota columns to user_analysis_quota
ALTER TABLE user_analysis_quota
ADD COLUMN monthly_limit INT NOT NULL DEFAULT 2,
ADD COLUMN monthly_used_count INT NOT NULL DEFAULT 0,
ADD COLUMN monthly_reset_date DATE;

-- Initialize monthly_reset_date for existing users
UPDATE user_analysis_quota
SET monthly_reset_date = DATE_FORMAT(DATE_ADD(NOW(), INTERVAL 1 MONTH), '%Y-%m-01')
WHERE monthly_reset_date IS NULL;

-- Create analysis_temporary_notes table
CREATE TABLE analysis_temporary_notes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    note_date DATE NOT NULL,
    condition_score INT NOT NULL,
    symptoms TEXT,
    additional_note TEXT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_analysis_temp_note_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_analysis_temp_note_user_date (user_id, note_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
