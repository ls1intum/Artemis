package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.domain.scores.TeamScore;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.TeamScoreRepository;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresAggregatedInformation;
import de.tum.in.www1.artemis.web.rest.dto.ExerciseScoresDTO;

/**
 * Service to efficiently calculate the statistics for the exercise-scores-chart.component.ts in the client
 * <p>
 * This services uses the participant scores tables for performance reason
 */
@Service
public class ExerciseScoresChartService {

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public ExerciseScoresChartService(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            ParticipantScoreRepository participantScoreRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participantScoreRepository = participantScoreRepository;
    }

    /**
     * Get the score of a user, the best score and the average score in the exercises
     *
     * @param user      the user for whom to get the individual score
     * @param exercises the exercises to consider
     * @return an unmodifiable list of the exercise scores
     */
    public List<ExerciseScoresDTO> getExerciseScores(Set<Exercise> exercises, User user) {
        if (user == null || exercises == null) {
            throw new IllegalArgumentException();
        }
        if (exercises.isEmpty()) {
            return List.of();
        }
        // Getting the score of the student in the exercises
        Map<Long, StudentScore> individualExerciseIdToStudentScore = getScoreOfStudentForIndividualExercises(user,
                exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet()));
        Map<Long, TeamScore> teamExerciseIdToTeamScore = getScoreOfStudentForTeamExercises(user,
                exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet()));
        // Getting the average and max scores in the exercise
        Map<Long, ExerciseScoresAggregatedInformation> exerciseIdToAggregatedInformation = participantScoreRepository.getAggregatedExerciseScoresInformation(exercises).stream()
                .collect(Collectors.toMap(ExerciseScoresAggregatedInformation::getExerciseId, exerciseScoresAggregatedInformation -> exerciseScoresAggregatedInformation));

        return exercises.stream()
                .map(exercise -> createExerciseScoreDTO(exerciseIdToAggregatedInformation, individualExerciseIdToStudentScore, teamExerciseIdToTeamScore, exercise)).toList();
    }

    private Map<Long, TeamScore> getScoreOfStudentForTeamExercises(User user, Set<Exercise> teamExercises) {
        return teamScoreRepository.findAllByExerciseAndUserWithEagerExercise(teamExercises, user).stream()
                .collect(Collectors.toMap(teamScore -> teamScore.getExercise().getId(), teamScore -> teamScore));
    }

    private Map<Long, StudentScore> getScoreOfStudentForIndividualExercises(User user, Set<Exercise> individualExercises) {
        return studentScoreRepository.findAllByExerciseAndUserWithEagerExercise(individualExercises, user).stream()
                .collect(Collectors.toMap(studentScore -> studentScore.getExercise().getId(), studentSore -> studentSore));
    }

    private ExerciseScoresDTO createExerciseScoreDTO(Map<Long, ExerciseScoresAggregatedInformation> exerciseIdToAggregatedInformation,
            Map<Long, StudentScore> individualExerciseIdToStudentScore, Map<Long, TeamScore> teamExerciseIdToTeamScore, Exercise exercise) {
        ExerciseScoresDTO exerciseScoresDTO = new ExerciseScoresDTO(exercise);

        ExerciseScoresAggregatedInformation aggregatedInformation = exerciseIdToAggregatedInformation.get(exercise.getId());

        if (aggregatedInformation == null || aggregatedInformation.getAverageScoreAchieved() == null || aggregatedInformation.getMaxScoreAchieved() == null) {
            exerciseScoresDTO.averageScoreAchieved = 0D;
            exerciseScoresDTO.maxScoreAchieved = 0D;
        }
        else {
            exerciseScoresDTO.averageScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.getAverageScoreAchieved(),
                    exercise.getCourseViaExerciseGroupOrCourseMember());
            exerciseScoresDTO.maxScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.getMaxScoreAchieved(),
                    exercise.getCourseViaExerciseGroupOrCourseMember());
        }

        ParticipantScore participantScore = exercise.getMode().equals(ExerciseMode.INDIVIDUAL) ? individualExerciseIdToStudentScore.get(exercise.getId())
                : teamExerciseIdToTeamScore.get(exercise.getId());
        exerciseScoresDTO.scoreOfStudent = participantScore == null || participantScore.getLastRatedScore() == null ? 0D
                : roundScoreSpecifiedByCourseSettings(participantScore.getLastScore(), exercise.getCourseViaExerciseGroupOrCourseMember());
        return exerciseScoresDTO;
    }
}
