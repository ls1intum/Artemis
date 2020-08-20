package de.tum.in.www1.artemis.service;

import static java.util.stream.Collectors.toSet;

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
     * @param exercise exercise
     * @return list of student score objects for that exercise
     */
    public List<StudentScore> getExerciseStudentScores(Exercise exercise) {
        return studentScoresRepository.findAllByExerciseId(exercise.getId());
    }

    /**
     * Returns all StudentScores for course.
     *
     * @param course course
     * @return list of student score objects for that course
     */
    public List<StudentScore> getCourseStudentScores(Course course) {
        Set<Exercise> exercises = course.getExercises();

        Set<Long> exerciseIds = exercises.stream().map(exercise -> exercise.getId()).collect(toSet());

        return studentScoresRepository.findAllByExerciseIdIn(exerciseIds);
    }
}
