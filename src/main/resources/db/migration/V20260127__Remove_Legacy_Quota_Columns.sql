-- Remove legacy weekly quota columns and monthlyLimit (replaced by constant)
ALTER TABLE user_analysis_quota
DROP COLUMN weekly_limit,
DROP COLUMN weekly_used_count,
DROP COLUMN weekly_reset_date,
DROP COLUMN monthly_limit;
