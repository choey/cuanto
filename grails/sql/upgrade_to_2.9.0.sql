------------------------------------
-- Initialize TestCase.analysisCount
------------------------------------

-- create temporary table
CREATE TABLE tmp_analysis_count
AS SELECT tout.test_case_id, count(*) as ct 
FROM test_outcome tout, analysis_state anlz
WHERE anlz.is_analyzed IS TRUE AND tout.analysis_state_id = anlz.id
GROUP BY tout.test_case_id;

-- create temporary index
CREATE INDEX tmpidx_test_case_analysis_count ON tmp_analysis_count(test_case_id,ct);

-- initialize analysis_count
UPDATE test_case tc SET tc.analysis_count =
(select ct from tmp_analysis_count where tmp_analysis_count.test_case_id = tc.id);

-- clean up temporary table
DROP TABLE tmp_analysis_count;

------------------------------------------------
-- Add FK for test_outcome.test_outcome_stats_id
------------------------------------------------ 

ALTER TABLE test_outcome ADD INDEX FK_TEST_OUTCOME_STATS (test_outcome_stats_id), ADD CONSTRAINT FK_TEST_OUTCOME_STATS FOREIGN KEY (test_outcome_stats_id) REFERENCES test_outcome_stats (id)
