package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.repository.StudentScoresRepository;

@Service
public class StudentScoresService {

    private final StudentScoresRepository studentScoresRepository;

    public StudentScoresService(StudentScoresRepository studentScoresRepository) {
        this.studentScoresRepository = studentScoresRepository;
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

    public void removeResult(Result deletedResult) {
        studentScoresRepository.deleteByResultId(deletedResult.getId());
    }

    public void updateResult(Result updatedResult) {
        var studentScore = studentScoresRepository.findByResultId(updatedResult.getId());
        if (studentScore.isPresent()) {
            studentScore.get().setScore(updatedResult.getScore());
            studentScoresRepository.save(studentScore.get());
        }
    }

    public void addNewResult(Result newResult) {
        // ignore unrated results
        if (newResult.isRated() != Boolean.TRUE) {
            return;
        }
        // TODO: handle 2 different cases:
        // 1) there is already an existing student score for a result with the same participation (i.e. the same exercise id and the same user id): update this student score
        // accordingly (this happens for programming exercises and for the 2nd/3rd correction of manual exercises) only in case the new result is rated
        // 2) there is no student score for the same participation yet: create a new one

    }
}
