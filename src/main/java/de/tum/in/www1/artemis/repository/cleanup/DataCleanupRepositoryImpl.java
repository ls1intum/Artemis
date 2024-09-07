package de.tum.in.www1.artemis.repository.cleanup;

import java.time.ZonedDateTime;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DataCleanupRepositoryImpl implements DataCleanupRepository {

    // transactinal ok, because of delete statements

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void deleteOrphans() {
        entityManager.createQuery("DELETE FROM Feedback f WHERE f.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM LongFeedbackText lft WHERE lft.feedback IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM StudentScore ps WHERE ps.user IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM TeamScore ps WHERE ps.team IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM Rating rr WHERE rr.result IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM Result r WHERE r.participation IS NULL AND r.submission IS NULL").executeUpdate();
        entityManager.createQuery("DELETE FROM PlagiarismComparison pc WHERE pc.submissionA IS NULL OR pc.submissionB IS NULL").executeUpdate();
    }

    @Override
    @Transactional
    public void deletePlagiarismComparisons(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager.createQuery("""
                    DELETE FROM PlagiarismComparison pc
                    WHERE pc.status = de.tum.in.www1.artemis.domain.plagiarism.PlagiarismStatus.NONE
                    AND pc.id IN (
                        SELECT pc.id
                        FROM PlagiarismComparison pc
                        JOIN Submission s1 ON pc.submissionA.submissionId = s1.id
                        JOIN Submission s2 ON pc.submissionB.submissionId = s2.id
                        JOIN Participation p1 ON s1.participation.id = p1.id
                        JOIN Participation p2 ON s2.participation.id = p2.id
                        JOIN Exercise e1 ON p1.exercise.id = e1.id
                        JOIN Exercise e2 ON p2.exercise.id = e2.id
                        JOIN Course c1 ON e1.course.id = c1.id
                        JOIN Course c2 ON e2.course.id = c2.id
                        WHERE LEAST(c1.endDate, c2.endDate) < :deleteTo AND
                        GREATEST(c1.startDate, c2.startDate) > :deleteFrom
                    )
                """).setParameter("deleteTo", deleteTo).setParameter("deleteFrom", deleteFrom).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteNonRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager.createQuery("""
                    DELETE FROM Result r
                    WHERE r.rated = false
                      AND r.participation IS NOT NULL
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
    @Transactional
    public void deleteRatedResults(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        // delete all rated results that are not latest rated for a participation for courses conducted within a specific date range
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

    @Override
    @Transactional
    public void deleteSubmissionVersions(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        entityManager.createQuery("""
                    DELETE FROM SubmissionVersion sv
                    WHERE sv.submission.id IS NOT NULL
                      AND EXISTS (
                          SELECT 1
                          FROM Exam ex
                          JOIN Course c ON ex.course = c
                          JOIN ExerciseGroup eg ON eg.exam = ex
                          JOIN Exercise e ON e.exerciseGroup = eg
                          WHERE e = sv.submission.participation.exercise
                            AND c.endDate < :deleteTo
                            AND c.endDate > :deleteFrom
                      )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();
    }

    @Override
    @Transactional
    public void deleteFeedback(ZonedDateTime deleteFrom, ZonedDateTime deleteTo) {
        // old feedback items that are not part of latest results
        entityManager.createQuery("""
                    DELETE FROM Feedback f
                    WHERE f.result.id IN (
                        SELECT r.id
                        FROM Result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE r.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id
                        ) AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();

        // old long feedback text that is not part of latest results
        entityManager.createQuery("""
                    DELETE FROM LongFeedbackText lft
                    WHERE lft.feedback.id IN (
                        SELECT f.id
                        FROM Feedback f
                        JOIN f.result r
                        JOIN r.participation p
                        JOIN p.exercise e
                        JOIN e.course c
                        WHERE f.result.id NOT IN (
                            SELECT MAX(r2.id)
                            FROM Result r2
                            WHERE r2.participation.id = p.id
                        )
                        AND c.endDate < :deleteTo
                        AND c.startDate > :deleteFrom
                    )
                """).setParameter("deleteFrom", deleteFrom).setParameter("deleteTo", deleteTo).executeUpdate();
    }

}
