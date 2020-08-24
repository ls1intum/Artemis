package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;
import de.tum.in.www1.artemis.repository.StudentScoresRepository;

@Service
public class StudentScoresService {

    private final StudentScoresRepository studentScoresRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public StudentScoresService(StudentScoresRepository studentScoresRepository, StudentParticipationRepository studentParticipationRepository) {
        this.studentScoresRepository = studentScoresRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Returns all StudentScores for exercise.
     *
     * @param exerciseId id of the exercise
     * @return list of student score objects for that exercise
     */
    public List<StudentScore> getStudentScoresForExercise(Long exerciseId) {
        return studentScoresRepository.findAllByExerciseId(exerciseId);
    }

    /**
     * Returns all StudentScores for course.
     *
     * @param course course
     * @return list of student score objects for that course
     */
    public List<StudentScore> getStudentScoresForCourse(Course course) {
        Set<Exercise> exercises = course.getExercises();
        Set<Long> exerciseIds = new HashSet<>();

        for (Exercise exercise : exercises) {
            exerciseIds.add(exercise.getId());
        }

        return studentScoresRepository.findAllByExerciseIdIn(exerciseIds);
    }

    /**
     * Removes all StudentScores for result deletedResult.
     *
     * @param deletedResult result to be deleted
     */
    public void removeResult(Result deletedResult) {
        studentScoresRepository.deleteByResultId(deletedResult.getId());
    }

    /**
     * Updates all StudentScores for result updatedResult.
     *
     * @param updatedResult result to be updated
     */
    public void updateResult(Result updatedResult) {
        var studentScore = studentScoresRepository.findByResultId(updatedResult.getId());
        if (studentScore.isPresent()) {
            if (updatedResult.getScore() != null) {
                studentScore.get().setScore(updatedResult.getScore());
            } else {
                studentScore.get().setScore(0);
            }
            studentScoresRepository.save(studentScore.get());
        }
    }

    /**
     * Adds new StudentScores for result newResult.
     *
     * @param newResult result to be added
     */
    public void addNewResult(Result newResult) {
        // ignore unrated results
        if (newResult.isRated() != Boolean.TRUE) {
            return;
        }

        // TODO: handle 2 different cases:
        // 1) there is already an existing student score for a result with the same participation (i.e. the same exercise id and the same user id): update this student score
        // accordingly (this happens for programming exercises and for the 2nd/3rd correction of manual exercises) only in case the new result is rated
        // 2) there is no student score for the same participation yet: create a new one

        StudentParticipation participation = studentParticipationRepository.findById(newResult.getParticipation().getId()).get();
        var studentId = participation.getStudent().get().getId();
        var exerciseId = participation.getExercise().getId();

        var existingStudentIdAndExerciseId = studentScoresRepository.findByStudentIdAndExerciseId(studentId, exerciseId);

        if (existingStudentIdAndExerciseId.isPresent()) {
            if (existingStudentIdAndExerciseId.get().getResultId() == newResult.getId()) {
                updateResult(newResult);
            } else {
                existingStudentIdAndExerciseId.get().setResultId(newResult.getId());
                if (newResult.getScore() != null) {
                    existingStudentIdAndExerciseId.get().setScore(newResult.getScore());
                } else {
                    existingStudentIdAndExerciseId.get().setScore(0);
                }
                // does not work when called from PostPersist. Everything else works fine
                studentScoresRepository.save(existingStudentIdAndExerciseId.get());
            }
        }else {
            StudentScore newScore = new StudentScore(participation.getStudent().get().getId(), participation.getExercise().getId(), newResult.getId(), 0);

            if (newResult.getScore() != null) {
                newScore.setScore(newResult.getScore());
            }

            // does not work when called from PostPersist. Everything else works fine
            studentScoresRepository.save(newScore);
        }
    }
}
