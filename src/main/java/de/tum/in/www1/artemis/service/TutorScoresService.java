package de.tum.in.www1.artemis.service;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorScore;
import de.tum.in.www1.artemis.repository.TutorScoresRepository;

import static java.util.stream.Collectors.toSet;

@Service
public class TutorScoresService {

    private final TutorScoresRepository tutorScoresRepository;

    public TutorScoresService(TutorScoresRepository tutorScoresRepository) {
        this.tutorScoresRepository = tutorScoresRepository;
    }

    /**
     * Returns all TutorScores for exercise.
     *
     * @param exercise exercise
     * @return list of tutor score objet for that exercise
     */
    public List<TutorScore> getExerciseTutorScores(Exercise exercise) {
        return tutorScoresRepository.findAllByExerciseId(exercise.getId());
    }

    /**
     * Returns all TutorScores for course.
     *
     * @param course course
     * @return list of tutor score objects for that course
     */
    public List<TutorScore> getCourseTutorScores(Course course) {
        Set<Exercise> exercises = course.getExercises();

        Set<Long> exerciseIds = exercises.stream().map(exercise -> exercise.getId()).collect(toSet());

        return tutorScoresRepository.findAllByExerciseIdIn(exerciseIds);
    }
}
