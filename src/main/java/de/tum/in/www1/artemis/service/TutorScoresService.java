package de.tum.in.www1.artemis.service;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.leaderboard.tutor.TutorScore;
import de.tum.in.www1.artemis.repository.TutorScoresRepository;

@Service
public class TutorScoresService {

    private final TutorScoresRepository tutorScoresRepository;

    public TutorScoresService(TutorScoresRepository tutorScoresRepository) {
        this.tutorScoresRepository = tutorScoresRepository;
    }

    /**
     * Returns all TutorScores for exercise.
     *
     * @param exerciseId id of the exercise
     * @return list of tutor score objet for that exercise
     */
    public List<TutorScore> getTutorScoresForExercise(Long exerciseId) {
        return tutorScoresRepository.findAllByExerciseId(exerciseId);
    }

    /**
     * Returns all TutorScores for course.
     *
     * @param course course
     * @return list of tutor score objects for that course
     */
    public List<TutorScore> getTutorScoresForCourse(Course course) {
        Set<Exercise> exercises = course.getExercises();
        Set<Long> exerciseIds = new HashSet<>();

        for(Exercise ex: exercises) {
            exerciseIds.add(ex.getId());
        }

        return tutorScoresRepository.findAllByExerciseIdIn(exerciseIds);
    }
}
