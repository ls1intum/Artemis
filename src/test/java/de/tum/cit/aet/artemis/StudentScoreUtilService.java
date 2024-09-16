package de.tum.cit.aet.artemis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Service responsible for initializing the database with specific testdata related to student scores for use in integration tests.
 */
@Service
public class StudentScoreUtilService {

    @Autowired
    private StudentScoreRepository studentScoreRepository;

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
    public void createStudentScoreIsRated(Exercise exercise, User user, double score) {
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
        if (Boolean.TRUE.equals(result.isRated())) {
            studentScore.setLastRatedResult(result);
            studentScore.setLastRatedScore(result.getScore());
            studentScore.setLastRatedPoints(exercise.getMaxPoints() * result.getScore() / 100);
        }

        studentScoreRepository.save(studentScore);
    }
}
