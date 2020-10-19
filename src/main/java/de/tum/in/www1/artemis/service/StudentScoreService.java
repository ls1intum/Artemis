package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
     * Returns one StudentScores for exercise and student if there is one.
     *
     * @param student student user
     * @param exercise exercise
     * @return list of student score objects for that course
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
        deletedResult.setStudentScore(null);
        studentScoreRepository.deleteByResult(deletedResult);
    }

    /**
     * Updates all StudentScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    public void updateResult(Result updatedResult) {
        // ignore results without score or participation
        if (updatedResult.getScore() == null || updatedResult.getParticipation() == null || !Boolean.TRUE.equals(updatedResult.isRated())) {
            log.info("Weil kein Score, keine Participation oder Unrated");
            return;
        }

        if (updatedResult.getParticipation().getClass() != StudentParticipation.class) {
            log.info("Weil keine StudentParticipation");
            return;
        }

        var participation = (StudentParticipation) updatedResult.getParticipation();
        var student = participation.getStudent();
        var optionalExercise = exerciseRepository.findById(participation.getExercise().getId());
        // var exercise = participation.getExercise();

        if (student.isEmpty() || optionalExercise.isEmpty()) {
            log.info("Weil keine Student oder keine Exercise");
            return;
        }

        var exercise = optionalExercise.get();

        if (updatedResult.getStudentScore() != null) {
            log.info("Delete old StudentScore");
            studentScoreRepository.delete(updatedResult.getStudentScore());
        }

        StudentScore studentScore = new StudentScore(student.get(), exercise, updatedResult);
        studentScore.setScore(updatedResult.getScore());

        log.info("Insert StudentScore: " + studentScore + " mit Exercise: " + exercise + ", Student: " + student.get() + " und Result: " + updatedResult);
        studentScore = studentScoreRepository.save(studentScore);
        log.info("StudentScore: " + studentScore + " with Score: " + studentScore.getScore());
    }
}
