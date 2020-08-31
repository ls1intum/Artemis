package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
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
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;

@Service
public class StudentScoreService {

    private final Logger log = LoggerFactory.getLogger(StudentScoreService.class);

    private final StudentScoreRepository studentScoreRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public StudentScoreService(StudentScoreRepository studentScoreRepository, StudentParticipationRepository studentParticipationRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.studentParticipationRepository = studentParticipationRepository;
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void removeResult(Result deletedResult) {
        studentScoreRepository.deleteByResult(deletedResult);
    }

    /**
     * Updates all StudentScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateResult(Result updatedResult) {
        // ignore results without score
        if (updatedResult.getScore() == null) {
            return;
        }

        var existingStudentScore = studentScoreRepository.findByResult(updatedResult);
        if (existingStudentScore.isPresent()) {
            StudentScore studentScore = existingStudentScore.get();

            studentScore.setScore(updatedResult.getScore());

            studentScore = studentScoreRepository.save(studentScore);
            log.info("updated student score in db: " + studentScore);
        }
    }

    /**
     * Adds new StudentScores for result newResult.
     *
     * @param newResult result to be added
     */
    public void addNewResult(Result newResult) {
        // ignore unrated results and results without participation
        if (newResult.isRated() != Boolean.TRUE || newResult.getParticipation() == null || newResult.getParticipation().getId() == null) {
            return;
        }

        if (newResult.getParticipation().getClass() != StudentParticipation.class) {
            return;
        }

        var participation = studentParticipationRepository.findById(newResult.getParticipation().getId());

        if (participation.isEmpty()) {
            return;
        }

        // TODO: this call does not work
        // var existingStudentScores = getStudentScoreForStudentAndExercise(participation.get().getStudent().get(), participation.getExercise());
        var existingStudentScores = new ArrayList<StudentScore>();

        if (existingStudentScores.size() > 0) {
            var existingStudentScore = existingStudentScores.get(0);
            if (existingStudentScore.getResult().getId().equals(newResult.getId())) {
                updateResult(newResult);
            }
            else {
                existingStudentScore.setResult(newResult);
                if (newResult.getScore() != null) {
                    existingStudentScore.setScore(newResult.getScore());
                }
                else {
                    existingStudentScore.setScore(0);
                }

                var updatedScore = studentScoreRepository.save(existingStudentScore);
                log.info("update student score in db: " + updatedScore);
            }
        }
        else {
            StudentScore newScore = new StudentScore();
            newScore.setStudent(participation.get().getStudent().get());
            newScore.setExercise(participation.get().getExercise());
            newScore.setResult(newResult);

            if (newResult.getScore() != null) {
                newScore.setScore(newResult.getScore());
            } else {
                newScore.setScore(0);
            }

            newScore = studentScoreRepository.save(newScore);
            log.info("new student score in db: " + newScore);
        }
    }
}
