package de.tum.cit.aet.artemis.core.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile(PROFILE_CORE)
@Repository
public class CustomDataCleanupRepository implements DataCleanupRepository {

    // transactinal ok, because of delete statements

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void deleteOrphans() {
        // long text feedback and text block entities reference feedback
        // can be deleted for feedback without result
        entityManager.createQuery("""
                    DELETE FROM LongFeedbackText lft
                    WHERE lft.feedback IN (
                        SELECT f
                        FROM Feedback f
                        WHERE f.result IS NULL
                    )
                """).executeUpdate();
        entityManager.createQuery("""
                    DELETE FROM TextBlock tb
                    WHERE tb.feedback IN (SELECT f FROM Feedback f JOIN f.result WHERE f.result IS NULL)
                """).executeUpdate();
        entityManager.createQuery("DELETE FROM Feedback f WHERE f.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM StudentScore ps WHERE ps.user IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM TeamScore ps WHERE ps.team IS NULL").executeUpdate();
        // all long feedback text records that are part of feedback that is part of an orphan result
        entityManager.createQuery("""
                    DELETE FROM LongFeedbackText lft
                    WHERE lft.feedback.id IN (
                        SELECT f.id
                        FROM Feedback f
                        WHERE f.result.participation IS NULL AND f.result.submission IS NULL
                    )
                """).executeUpdate();
        // we cannot delete feedbacks that are part of orphan results because of text block relation
        entityManager.createQuery("""
                    DELETE FROM TextBlock tb
                    WHERE tb.feedback IN (SELECT f FROM Feedback f JOIN f.result r WHERE r.submission IS NULL AND r.participation IS NULL)
                """).executeUpdate();
        // finally, we can delete feedbacks that are connected to orphan results
        entityManager.createQuery("""
                    DELETE FROM Feedback f
                    WHERE f.result IN (SELECT r FROM Result r WHERE r.submission IS NULL AND r.participation IS NULL)
                """).executeUpdate();
        entityManager.createQuery("""
                    DELETE FROM Rating rt
                    WHERE rt.result IN (SELECT r FROM Result r where r.submission IS NULL AND r.participation IS NULL)
                """).executeUpdate();
        entityManager.createQuery("DELETE FROM Result r WHERE r.participation IS NULL AND r.submission IS NULL").executeUpdate();
    }

    @Override
    public List<Long> getUnnecessaryPlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        return entityManager.createQuery("""
                      SELECT pc.id
                      FROM PlagiarismComparison pc
                      JOIN Submission s1 ON pc.submissionA.submissionId = s1.id
                      JOIN Submission s2 ON pc.submissionB.submissionId = s2.id
                      JOIN s1.participation p1
                      JOIN s2.participation p2
                      LEFT JOIN p1.exercise e1
                      LEFT JOIN p2.exercise e2
                      LEFT JOIN e1.course c1
                      LEFT JOIN e2.course c2
                      WHERE pc.status = de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE AND
                      LEAST(c1.endDate, c2.endDate) < :deleteTo AND
                      GREATEST(c1.startDate, c2.startDate) > :deleteFrom
                """, Long.class).setParameter("deleteTo", deleteTo).setParameter("deleteFrom", deleteFrom).getResultList();
    }

    @Override
    public void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        // old long feedback text that is not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM LongFeedbackText lft
                    WHERE lft.feedback IN (
                        SELECT f
                        FROM Feedback f
                        JOIN f.result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.rated=false
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // old text block that is not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM TextBlock tb
                    WHERE tb.feedback IN (
                        SELECT f
                        FROM Feedback f
                        JOIN f.result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.rated=false
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // old feedback items that are not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM Feedback f
                    WHERE f.result IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.rated=false AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // delete participation scores
        entityManager.createQuery("""
                    DELETE FROM ParticipantScore ps
                    WHERE ps.lastResult IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.rated=false AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        entityManager.createQuery("""
                    DELETE FROM ParticipantScore ps
                    WHERE ps.lastRatedResult IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.rated=false AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        entityManager.createQuery("""
                    DELETE FROM Result r
                    WHERE r.rated = false
                      AND r.participation IS NOT NULL
                      AND r.participation.exercise IS NOT NULL
                      AND EXISTS (
                          SELECT 1
                          FROM Course c
                          JOIN Exercise e ON e.course = c
                          WHERE e = r.participation.exercise
                            AND c.endDate < :deleteTo
                            AND c.startDate > :deleteFrom
                      )
                """).setParameter("deleteTo", deleteTo).setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    public void deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        // delete all rated results that are not latest rated for a participation for courses conducted within a specific date range, also delete all related feedback entities
        // beforehand
        // old long feedback text that is not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM LongFeedbackText lft
                    WHERE lft.feedback IN (
                        SELECT f
                        FROM Feedback f
                        JOIN f.result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE f.result.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id AND r2.rated=true
                        )
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // old text block that is not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM TextBlock tb
                    WHERE tb.feedback IN (
                        SELECT f
                        FROM Feedback f
                        JOIN f.result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE f.result.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id AND r2.rated=true
                        )
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // old feedback items that are not part of latest rated results
        entityManager.createQuery("""
                    DELETE FROM Feedback f
                    WHERE f.result IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id AND r2.rated=true
                        ) AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // delete participation scores
        entityManager.createQuery("""
                    DELETE FROM ParticipantScore ps
                    WHERE ps.lastResult IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id AND r2.rated=true
                        ) AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        entityManager.createQuery("""
                    DELETE FROM ParticipantScore ps
                    WHERE ps.lastRatedResult IN (
                        SELECT r
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id AND r2.rated=true
                        ) AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // exercise is fetched eagerly for participations
        entityManager.createQuery("""
                    DELETE FROM Result r
                    WHERE r.rated = true
                      AND r.participation IS NOT NULL
                      AND r.participation.exercise IS NOT NULL
                      AND EXISTS (
                          SELECT 1
                          FROM Course c
                          JOIN Exercise e ON e.course = c
                          WHERE e = r.participation.exercise
                            AND c.endDate < :deleteTo
                            AND c.startDate > :deleteFrom
                      )
                      AND r.id NOT IN (
                          SELECT MAX(r2.id)
                          FROM Result r2
                          WHERE r2.participation = r.participation
                            AND r2.rated = true
                      )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();
    }
}
