package de.tum.in.www1.artemis;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;

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
        studentScoreRepository.save(studentScore);
    }
}
