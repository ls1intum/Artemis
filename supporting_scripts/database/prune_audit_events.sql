-- ---------------------------------------------------------------------------------------------------------------------
-- Manual pruning of jhi_persistent_audit_event (MySQL 8)
--
-- The nightly job in AutomaticAuditEventCleanupService does the same work in capped batches, but on an instance where the
-- table has never been pruned it deliberately drains the backlog over several nights. This script exists to do it in one
-- maintenance window instead, so the footprint shrinks immediately - and, if run before the upgrade that introduces the
-- three-table split, so that the migration has far less data to copy.
--
-- It applies exactly the retention scheme of the nightly job:
--
--   * the login record (successful, failed and passkey logins, and logouts)  -> 365 days by default
--   * everything else (deliberate actions on courses, exercises, exams,      -> 1825 days by default
--     accounts; rows whose type is unknown or NULL)
--
-- Safe to interrupt and safe to re-run: each batch is its own transaction, so stopping it mid-way simply leaves the
-- remainder for the next run. It never touches rows inside the retention period.
--
-- Usage (from a machine that can reach the database, e.g. via ssh to the database host):
--
--     mysql Artemis < prune_audit_events.sql          # step 0 and 1 only report; nothing is deleted until step 3
--
-- Read the output of steps 0-2, then uncomment the CALL statements in step 3.
-- ---------------------------------------------------------------------------------------------------------------------

-- =====================================================================================================================
-- Step 0: how big is the problem? Approximate but instant - it reads table metadata, not the rows.
-- =====================================================================================================================
SELECT table_name,
       table_rows                                        AS approx_rows,
       ROUND(data_length / 1024 / 1024 / 1024, 2)        AS data_gb,
       ROUND(index_length / 1024 / 1024 / 1024, 2)       AS index_gb
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('jhi_persistent_audit_event', 'jhi_persistent_audit_evt_data');

-- =====================================================================================================================
-- Step 1: the index the pruning needs.
--
-- Without an index on event_date, every batch scans and sorts the whole table. Liquibase creates this index too, guarded
-- by a "not indexExists" precondition, so creating it here simply makes that changeset a no-op later.
--
-- This is an online operation (MySQL adds secondary indexes in place, without an exclusive lock), but it still takes time
-- proportional to the table size. Run it first and let it finish.
-- =====================================================================================================================
SET @index_missing = (SELECT COUNT(*) = 0
                      FROM information_schema.statistics
                      WHERE table_schema = DATABASE()
                        AND table_name = 'jhi_persistent_audit_event'
                        AND index_name = 'idx_persistent_audit_event_date');
SET @create_index = IF(@index_missing,
                       'ALTER TABLE jhi_persistent_audit_event ADD INDEX idx_persistent_audit_event_date (event_date)',
                       'DO 0 /* index already present */');
PREPARE stmt FROM @create_index;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =====================================================================================================================
-- Step 2: what would be deleted? Run this before step 3 to see the size of the job.
--
-- These counts use the index from step 1. On a very large table they still take a while; that is expected.
-- =====================================================================================================================
SELECT 'login records older than 365 days' AS bucket,
       COUNT(*)                            AS rows_to_delete
FROM jhi_persistent_audit_event
WHERE event_type IN ('AUTHENTICATION_SUCCESS', 'AUTHENTICATION_PASSKEY_SUCCESS', 'SAML2_AUTHENTICATION_SUCCESS', 'AUTHENTICATION_FAILURE', 'LOGOUT_SUCCESS')
  AND event_date < DATE_SUB(NOW(), INTERVAL 365 DAY)
UNION ALL
SELECT 'everything else older than 1825 days' AS bucket,
       COUNT(*)                               AS rows_to_delete
FROM jhi_persistent_audit_event
WHERE (event_type IS NULL
    OR event_type NOT IN ('AUTHENTICATION_SUCCESS', 'AUTHENTICATION_PASSKEY_SUCCESS', 'SAML2_AUTHENTICATION_SUCCESS', 'AUTHENTICATION_FAILURE', 'LOGOUT_SUCCESS'))
  AND event_date < DATE_SUB(NOW(), INTERVAL 1825 DAY);

-- =====================================================================================================================
-- Step 3: the batched delete.
--
-- Why batched rather than one DELETE: a single statement over millions of rows holds row locks for its whole duration,
-- writes one enormous undo log entry, and produces a binlog event that a replica applies as one transaction. Batches of
-- a few thousand rows keep each of those bounded, and the optional pause between batches gives replication room to keep up.
--
-- Children are deleted before parents, because jhi_persistent_audit_evt_data's foreign key is ON DELETE RESTRICT: the
-- database rejects deleting a parent whose data rows still exist.
--
-- The batch is materialised into a temporary table first so that both deletes act on exactly the same rows. Selecting the
-- oldest rows twice with ORDER BY ... LIMIT would not guarantee that, because rows sharing an event_date can be returned
-- in a different order, which would leave a parent deleted while its children remain - and the foreign key would abort it.
-- =====================================================================================================================
DROP PROCEDURE IF EXISTS artemis_prune_audit_events;

DELIMITER $$

CREATE PROCEDURE artemis_prune_audit_events(
    IN p_bucket VARCHAR(16),         -- 'LOGIN' for the login record, 'OTHER' for everything else
    IN p_retention_days INT,         -- rows strictly older than this many days are deleted
    IN p_batch_size INT,             -- rows per batch; 5000 is a good default
    IN p_max_batches INT,            -- upper bound on batches, so a run cannot last indefinitely
    IN p_sleep_seconds DECIMAL(4, 1) -- pause between batches; 0 for none, 0.5 is gentle on replicas
)
BEGIN
    DECLARE v_batch INT DEFAULT 0;
    DECLARE v_deleted INT DEFAULT 0;
    DECLARE v_total INT DEFAULT 0;
    DECLARE v_drained BOOLEAN DEFAULT FALSE;

    IF p_bucket NOT IN ('LOGIN', 'OTHER') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'p_bucket must be LOGIN or OTHER';
    END IF;

    DROP TEMPORARY TABLE IF EXISTS tmp_audit_event_ids;
    CREATE TEMPORARY TABLE tmp_audit_event_ids (event_id BIGINT NOT NULL PRIMARY KEY);

    prune_loop: WHILE v_batch < p_max_batches DO
        DELETE FROM tmp_audit_event_ids;

        IF p_bucket = 'LOGIN' THEN
            INSERT INTO tmp_audit_event_ids (event_id)
            SELECT event_id
            FROM jhi_persistent_audit_event
            WHERE event_type IN ('AUTHENTICATION_SUCCESS', 'AUTHENTICATION_PASSKEY_SUCCESS', 'SAML2_AUTHENTICATION_SUCCESS', 'AUTHENTICATION_FAILURE', 'LOGOUT_SUCCESS')
              AND event_date < DATE_SUB(NOW(), INTERVAL p_retention_days DAY)
            ORDER BY event_date
            LIMIT p_batch_size;
        ELSE
            -- "NOT IN" alone would skip rows with no type forever, because NULL NOT IN (...) is unknown, not true.
            INSERT INTO tmp_audit_event_ids (event_id)
            SELECT event_id
            FROM jhi_persistent_audit_event
            WHERE (event_type IS NULL
                OR event_type NOT IN ('AUTHENTICATION_SUCCESS', 'AUTHENTICATION_PASSKEY_SUCCESS', 'SAML2_AUTHENTICATION_SUCCESS', 'AUTHENTICATION_FAILURE', 'LOGOUT_SUCCESS'))
              AND event_date < DATE_SUB(NOW(), INTERVAL p_retention_days DAY)
            ORDER BY event_date
            LIMIT p_batch_size;
        END IF;

        SELECT COUNT(*) INTO v_deleted FROM tmp_audit_event_ids;
        IF v_deleted = 0 THEN
            -- Nothing expired left in this bucket.
            SET v_drained = TRUE;
            LEAVE prune_loop;
        END IF;

        START TRANSACTION;
        DELETE data_row
        FROM jhi_persistent_audit_evt_data data_row
                 JOIN tmp_audit_event_ids batch ON data_row.event_id = batch.event_id;
        DELETE event
        FROM jhi_persistent_audit_event event
                 JOIN tmp_audit_event_ids batch ON event.event_id = batch.event_id;
        COMMIT;

        SET v_total = v_total + v_deleted;
        SET v_batch = v_batch + 1;

        IF p_sleep_seconds > 0 THEN
            DO SLEEP(p_sleep_seconds);
        END IF;
    END WHILE;

    DROP TEMPORARY TABLE IF EXISTS tmp_audit_event_ids;

    -- If the batch cap was reached rather than the bucket running dry, expired rows may remain; re-run to continue.
    SELECT p_bucket                                                                                    AS bucket,
           p_retention_days                                                                            AS retention_days,
           v_total                                                                                     AS deleted_rows,
           IF(v_drained, 'done', 'batch cap reached, re-run to continue')                              AS status;
END$$

DELIMITER ;

-- Uncomment to run. Start with the login record: it is the bulk of the table, so it frees the most space per row deleted.
-- The arguments are (bucket, retention days, batch size, max batches, seconds between batches).
--
-- CALL artemis_prune_audit_events('LOGIN', 365, 5000, 10000, 0.2);
-- CALL artemis_prune_audit_events('OTHER', 1825, 5000, 1000, 0.2);

-- Reclaiming the freed space on disk needs a table rebuild, which InnoDB does not do implicitly. The deleted pages are
-- reused by future inserts, so this is only worth doing if the space has to be returned to the filesystem:
--
-- ALTER TABLE jhi_persistent_audit_event ENGINE = InnoDB;
-- ALTER TABLE jhi_persistent_audit_evt_data ENGINE = InnoDB;

-- DROP PROCEDURE IF EXISTS artemis_prune_audit_events;
