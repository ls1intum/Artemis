package de.tum.in.www1.artemis.service;

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
     * Will get get the score of the requesting user and the average score reached in the exercises
     *
     * @param user      the user for whom to get the exercise scores
     * @param exercises the exercises for which to get the exercise scores
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
        // Getting the students scores in the exercises
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
                exerciseScoresDTO.averageScoreAchieved = 0L;
                exerciseScoresDTO.maxScoreAchieved = 0L;
            }
            else {
                exerciseScoresDTO.averageScoreAchieved = aggregatedInformation.getAverageScoreAchieved().longValue();
                exerciseScoresDTO.maxScoreAchieved = aggregatedInformation.getMaxScoreAchieved();
            }

            ParticipantScore participantScore;
            if (exercise.getMode().equals(ExerciseMode.INDIVIDUAL)) {
                participantScore = individualExerciseIdToStudentScore.get(exercise.getId());
            }
            else {
                participantScore = teamExerciseIdToTeamScore.get(exercise.getId());
            }
            if (participantScore == null || participantScore.getLastRatedScore() == null) {
                exerciseScoresDTO.scoreOfStudent = 0L;
            }
            else {
                exerciseScoresDTO.scoreOfStudent = participantScore.getLastScore();
            }

            exerciseScoresDTOs.add(exerciseScoresDTO);
        }
        return exerciseScoresDTOs;
    }
}
