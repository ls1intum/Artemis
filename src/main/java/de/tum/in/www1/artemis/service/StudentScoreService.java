package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;

@Service
public class StudentScoreService {

    private final Logger log = LoggerFactory.getLogger(StudentScoreService.class);

    private final StudentScoreRepository studentScoreRepository;

    private final ExerciseRepository exerciseRepository;

    public StudentScoreService(StudentScoreRepository studentScoreRepository, ExerciseRepository exerciseRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.exerciseRepository = exerciseRepository;
    }

    /**
     * Returns all StudentScores for exercise.
     *
     * @param exercise the exercise
     * @return list of student score objects for that exercise
     */
    public List<StudentScore> getStudentScoresForExercise(Exercise exercise) {
        return studentScoreRepository.findAllByExercise(exercise);
    }

    /**
     * Returns all StudentScores for course.
     *
     * @param course course
     * @return list of student score objects for that course
     */
    public List<StudentScore> getStudentScoresForCourse(Course course) {
        return studentScoreRepository.findAllByExerciseIdIn(course.getExercises());
    }

    /**
     * Delete all StudentScores for exercise.
     *
     * @param exercise exercise
     */
    public void deleteStudentScoresForExercise(Exercise exercise) {
        var scores = getStudentScoresForExercise(exercise);

        for (StudentScore score : scores) {
            studentScoreRepository.delete(score);
        }
    }

    /**
     * Returns one StudentScores for exercise and student if one exists.
     *
     * @param student student user
     * @param exercise exercise
     * @return student score for student and exercise if it exists
     */
    public Optional<StudentScore> getStudentScoreForStudentAndExercise(User student, Exercise exercise) {
        return studentScoreRepository.findByStudentAndExercise(student, exercise);
    }

    /**
     * Removes StudentScore for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    public void removeResult(Result deletedResult) {
        studentScoreRepository.deleteByResult(deletedResult);
    }

    /**
     * Updates all StudentScores for result updatedResult.
     *
     * @param result result to be updated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(Result result) {

        // ignore results without score or participation
        if (result.getScore() == null || result.getParticipation() == null || !Boolean.TRUE.equals(result.isRated())) {
            return;
        }

        if (result.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = (StudentParticipation) result.getParticipation();
        var student = participation.getStudent();
        var exercise = exerciseRepository.findById(participation.getExercise().getId());

        if (student.isEmpty() || exercise.isEmpty()) {
            return;
        }

        Optional<StudentScore> studentScoreConnectedToResult = studentScoreRepository.findByResult(result);

        if (studentScoreConnectedToResult.isPresent()) {

            StudentScore studentScore = studentScoreConnectedToResult.get();
            studentScore.setResult(result);
            studentScore.setScore(result.getScore());

            studentScore = studentScoreRepository.saveAndFlush(studentScore);
            log.info("Updated StudentScore: " + studentScore);
        }
        else {
            StudentScore studentScore = new StudentScore(student.get(), exercise.get(), result);
            studentScore.setScore(result.getScore());

            studentScoreRepository.saveAndFlush(studentScore);
            log.info("Created StudentScore: " + studentScore);
        }
    }
}
