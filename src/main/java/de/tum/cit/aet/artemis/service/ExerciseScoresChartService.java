package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseMode;
import de.tum.cit.aet.artemis.domain.scores.ParticipantScore;
import de.tum.cit.aet.artemis.domain.scores.StudentScore;
import de.tum.cit.aet.artemis.domain.scores.TeamScore;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseScoresAggregatedInformation;
import de.tum.cit.aet.artemis.web.rest.dto.ExerciseScoresDTO;

/**
 * Service to efficiently calculate the statistics for the exercise-scores-chart.component.ts in the client
 * <p>
 * This services uses the participant scores tables for performance reason
 */
@Profile(PROFILE_CORE)
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
                .collect(Collectors.toMap(ExerciseScoresAggregatedInformation::exerciseId, exerciseScoresAggregatedInformation -> exerciseScoresAggregatedInformation));

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
        ExerciseScoresAggregatedInformation aggregatedInformation = exerciseIdToAggregatedInformation.get(exercise.getId());

        double averageScoreAchieved = 0D;
        double maxScoreAchieved = 0D;

        if (aggregatedInformation != null && aggregatedInformation.averageScoreAchieved() != null && aggregatedInformation.maxScoreAchieved() != null) {
            averageScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.averageScoreAchieved(), exercise.getCourseViaExerciseGroupOrCourseMember());
            maxScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.maxScoreAchieved(), exercise.getCourseViaExerciseGroupOrCourseMember());
        }

        ParticipantScore participantScore = exercise.getMode().equals(ExerciseMode.INDIVIDUAL) ? individualExerciseIdToStudentScore.get(exercise.getId())
                : teamExerciseIdToTeamScore.get(exercise.getId());
        final double scoreOfStudent = participantScore == null || participantScore.getLastRatedScore() == null ? 0D
                : roundScoreSpecifiedByCourseSettings(participantScore.getLastScore(), exercise.getCourseViaExerciseGroupOrCourseMember());

        return ExerciseScoresDTO.of(exercise, scoreOfStudent, averageScoreAchieved, maxScoreAchieved);
    }
}
