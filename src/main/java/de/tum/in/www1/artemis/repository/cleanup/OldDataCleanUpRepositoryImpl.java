package de.tum.in.www1.artemis.repository.cleanup;

import java.time.ZonedDateTime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

// TODO Dmytro: modify queries to use deleteFrom and deleteTo
@Repository
public class OldDataCleanUpRepositoryImpl implements OldDataCleanUpRepository {

    // transactinal ok, because of delete statements

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void deleteOrphans(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager.createQuery("DELETE FROM Feedback f WHERE f.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM LongFeedbackText lft WHERE lft.feedback IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM ParticipantScore ps WHERE ps.team IS NULL AND ps.user IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM ResultRating rr WHERE rr.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM AssessmentNote an WHERE an.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM Result r WHERE r.participation IS NULL AND r.submission IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM PlagiarismComparison pc WHERE pc.submissionA IS NULL AND pc.submissionB IS NULL").executeUpdate();
    }

    @Override
    @Transactional
    public void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager
                .createQuery("DELETE FROM PlagiarismComparison pc " + "WHERE pc.id IN (" + "    SELECT pc.id " + "    FROM PlagiarismComparison pc " + "    JOIN pc.submissionA s1 "
                        + "    JOIN pc.submissionB s2 " + "    JOIN s1.participation p1 " + "    JOIN s2.participation p2 " + "    JOIN p1.exercise e1 "
                        + "    JOIN p2.exercise e2 " + "    JOIN e1.course c1 " + "    JOIN e2.course c2 " + "    WHERE GREATEST(c1.endDate, c2.endDate) < :deleteFrom" + ")")
                .setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager.createQuery("DELETE FROM Result r " + "WHERE r.rated = false " + "AND r.participation.exercise.course.endDate < :deleteFrom")
                .setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteOldRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager
                .createQuery("DELETE FROM Result r " + "WHERE r.id IN (" + "    SELECT r.id " + "    FROM Result r " + "    WHERE r.rated = true "
                        + "    AND r.participation.exercise.course.endDate < :deleteFrom " + "    AND r.id NOT IN (" + "        SELECT r1.id " + "        FROM Result r1 "
                        + "        WHERE r1.participation = r.participation " + "        ORDER BY r1.completionDate DESC " + "        LIMIT 1" + "    )" + ")")
                .setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteOldSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager
                .createQuery("DELETE FROM SubmissionVersion sv " + "WHERE sv.submission.id IN (" + "    SELECT s.id " + "    FROM Submission s " + "    JOIN s.participation p "
                        + "    JOIN p.exercise e " + "    JOIN e.exerciseGroup eg " + "    JOIN eg.exam ex " + "    JOIN ex.course c " + "    WHERE c.endDate < :deleteFrom" + ")")
                .setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteOldFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager
                .createQuery("DELETE FROM Feedback f " + "WHERE f.result.id NOT IN (" + "    SELECT MAX(r.id) " + "    FROM Result r "
                        + "    WHERE r.participation = f.result.participation" + ") " + "AND f.result.participation.exercise.course.endDate < :deleteFrom")
                .setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public boolean existsDataForCleanup(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        Long count = (Long) entityManager.createQuery("""
                SELECT
                    CASE
                        WHEN (SELECT COUNT(f) FROM Feedback f WHERE f.result IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(lft) FROM LongFeedbackText lft WHERE lft.feedback IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(ps) FROM ParticipantScore ps WHERE ps.team IS NULL AND ps.user IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(rr) FROM ResultRating rr WHERE rr.result IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(an) FROM AssessmentNote an WHERE an.result IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(r) FROM Result r WHERE r.participation IS NULL AND r.submission IS NULL) > 0 THEN 1
                        WHEN (SELECT COUNT(pc) FROM PlagiarismComparison pc WHERE pc.submissionA IS NULL AND pc.submissionB IS NULL) > 0 THEN 1
                        WHEN (
                            SELECT COUNT(pc)
                            FROM PlagiarismComparison pc
                            JOIN pc.submissionA s1
                            JOIN pc.submissionB s2
                            JOIN s1.participation p1
                            JOIN s2.participation p2
                            JOIN p1.exercise e1
                            JOIN p2.exercise e2
                            JOIN e1.course c1
                            JOIN e2.course c2
                            WHERE GREATEST(c1.endDate, c2.endDate) < :deleteFrom
                        ) > 0 THEN 1
                        WHEN (
                            SELECT COUNT(r)
                            FROM Result r
                            WHERE r.rated = false
                            AND r.participation.exercise.course.endDate < :deleteFrom
                        ) > 0 THEN 1
                        WHEN (
                            SELECT COUNT(r.id)
                            FROM Result r
                            WHERE r.rated = true
                            AND r.participation.exercise.course.endDate < :deleteFrom
                            AND r.id NOT IN (
                                SELECT r1.id
                                FROM Result r1
                                WHERE r1.participation = r.participation
                                ORDER BY r1.completionDate DESC
                                LIMIT 1
                            )
                        ) > 0 THEN 1
                        WHEN (
                            SELECT COUNT(sv)
                            FROM SubmissionVersion sv
                            WHERE sv.submission.id IN (
                                SELECT s.id
                                FROM Submission s
                                JOIN s.participation p
                                JOIN p.exercise e
                                JOIN e.exerciseGroup eg
                                JOIN eg.exam ex
                                JOIN ex.course c
                                WHERE c.endDate < :deleteFrom
                            )
                        ) > 0 THEN 1
                        WHEN (
                            SELECT COUNT(f)
                            FROM Feedback f
                            WHERE f.result.id NOT IN (
                                SELECT MAX(r.id)
                                FROM Result r
                                WHERE r.participation = f.result.participation
                            )
                            AND f.result.participation.exercise.course.endDate < :deleteFrom
                        ) > 0 THEN 1
                        ELSE 0
                    END
                """).setParameter("deleteFrom", deleteFrom).getSingleResult();
        return count != null && count > 0;
    }

}
