package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundToNDecimalPlaces;

import java.util.ArrayList;
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

@Service
public class LearningAnalyticsService {

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public LearningAnalyticsService(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ParticipantScoreRepository participantScoreRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participantScoreRepository = participantScoreRepository;
    }

    /**
     * Get the score of a user, the best score and the average score in the exercises
     *
     * @param user      the user for whom to get the individual score
     * @param exercises the exercises for which to get the scores
     * @return list of the exercise scores
     */
    public List<ExerciseScoresDTO> getExerciseScores(Set<Exercise> exercises, User user) {
        if (user == null || exercises == null) {
            throw new IllegalArgumentException();
        }

        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());
        // Getting the average and max scores in the exercise
        Map<Long, ExerciseScoresAggregatedInformation> exerciseIdToAggregatedInformation = participantScoreRepository.getAggregatedExerciseScoresInformation(exercises).stream()
                .collect(Collectors.toMap(ExerciseScoresAggregatedInformation::getExerciseId, exerciseScoresAggregatedInformation -> exerciseScoresAggregatedInformation));
        Map<Long, StudentScore> individualExerciseIdToStudentScore = studentScoreRepository.findAllByExerciseAndUserWithEagerExercise(individualExercises, user).stream()
                .collect(Collectors.toMap(studentScore -> studentScore.getExercise().getId(), studentSore -> studentSore));
        Map<Long, TeamScore> teamExerciseIdToTeamScore = teamScoreRepository.findAllByExerciseAndUserWithEagerExercise(teamExercises, user).stream()
                .collect(Collectors.toMap(teamScore -> teamScore.getExercise().getId(), teamScore -> teamScore));

        List<ExerciseScoresDTO> exerciseScoresDTOs = new ArrayList<>();

        for (Exercise exercise : exercises) {
            ExerciseScoresDTO exerciseScoresDTO = new ExerciseScoresDTO();
            exerciseScoresDTO.exerciseId = exercise.getId();
            exerciseScoresDTO.exerciseTitle = exercise.getTitle();
            exerciseScoresDTO.releaseDate = exercise.getReleaseDate();
            exerciseScoresDTO.exerciseType = exercise.getStringRepresentationOfType();

            ExerciseScoresAggregatedInformation aggregatedInformation = exerciseIdToAggregatedInformation.get(exercise.getId());

            if (aggregatedInformation == null || aggregatedInformation.getAverageScoreAchieved() == null || aggregatedInformation.getMaxScoreAchieved() == null) {
                exerciseScoresDTO.averageScoreAchieved = 0D;
                exerciseScoresDTO.maxScoreAchieved = 0D;
            }
            else {
                exerciseScoresDTO.averageScoreAchieved = roundToNDecimalPlaces(aggregatedInformation.getAverageScoreAchieved(), 1);
                exerciseScoresDTO.maxScoreAchieved = roundToNDecimalPlaces(aggregatedInformation.getMaxScoreAchieved(), 0);
            }

            ParticipantScore participantScore = exercise.getMode().equals(ExerciseMode.INDIVIDUAL) ? individualExerciseIdToStudentScore.get(exercise.getId())
                    : teamExerciseIdToTeamScore.get(exercise.getId());
            exerciseScoresDTO.scoreOfStudent = participantScore == null || participantScore.getLastRatedScore() == null ? 0D
                    : roundToNDecimalPlaces(participantScore.getLastScore(), 0);

            exerciseScoresDTOs.add(exerciseScoresDTO);
        }
        return exerciseScoresDTOs;
    }
}
