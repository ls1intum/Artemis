package de.tum.in.www1.artemis.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.StudentScore;
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

        for (Exercise ex : exercises) {
            exerciseIds.add(ex.getId());
        }

        return studentScoresRepository.findAllByExerciseIdIn(exerciseIds);
    }
}
