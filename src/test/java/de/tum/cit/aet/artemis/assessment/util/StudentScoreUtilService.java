package de.tum.cit.aet.artemis.assessment.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.time.Instant;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Service responsible for initializing the database with specific testdata related to student scores for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class StudentScoreUtilService {

    @Autowired
    private StudentScoreRepository studentScoreRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Creates student score for given exercise and user.
     *
     * @param exercise the exercise to link the student score to
     * @param user     the user that is linked to the score
     * @param score    the score that the specified user has reached for the given exercise
     */
    public void createStudentScore(Exercise exercise, User user, double score) {
        final var studentScore = new StudentScore();
        studentScore.setExercise(exercise);
        studentScore.setUser(user);
        studentScore.setLastScore(score);
        studentScore.setLastPoints(exercise.getMaxPoints() * score / 100);
        studentScoreRepository.save(studentScore);
    }

    /**
     * Creates rated and normal score (which are set equal) for given exercise and user.
     *
     * @param exercise the exercise to link the student score to
     * @param user     the user that is linked to the score
     * @param score    the score and rated score that the specified user has reached for the given exercise
     */
    public void createRatedStudentScore(Exercise exercise, User user, double score) {
        final var studentScore = new StudentScore();
        studentScore.setExercise(exercise);
        studentScore.setUser(user);
        studentScore.setLastScore(score);
        studentScore.setLastPoints(exercise.getMaxPoints() * score / 100);
        studentScore.setLastRatedScore(score);
        studentScoreRepository.save(studentScore);
    }

    /**
     * Creates student score for given exercise and user.
     *
     * @param exercise the exercise to link the student score to
     * @param user     the user that is linked to the score
     * @param result   the result that the specified user has reached for the given exercise
     */
    public void createStudentScore(Exercise exercise, User user, Result result) {
        final var studentScore = new StudentScore();
        studentScore.setExercise(exercise);
        studentScore.setUser(user);
        studentScore.setLastResult(result);
        studentScore.setLastScore(result.getScore());
        studentScore.setLastPoints(exercise.getMaxPoints() * result.getScore() / 100);
        if (result.isRated()) {
            studentScore.setLastRatedResult(result);
            studentScore.setLastRatedScore(result.getScore());
            studentScore.setLastRatedPoints(exercise.getMaxPoints() * result.getScore() / 100);
        }

        studentScoreRepository.save(studentScore);
    }

    /**
     * Sets the lastModifiedDate of all student scores for the given user to the same instant.
     * This prevents the recency confidence heuristic from producing non-deterministic results
     * when saves happen across different seconds.
     *
     * @param user the user whose student scores should be normalized
     * @param date the instant to set as lastModifiedDate for all scores
     */
    @Transactional
    public void normalizeLastModifiedDates(User user, Instant date) {
        entityManager.createQuery("UPDATE StudentScore s SET s.lastModifiedDate = :date WHERE s.user = :user").setParameter("date", date).setParameter("user", user)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }
}
