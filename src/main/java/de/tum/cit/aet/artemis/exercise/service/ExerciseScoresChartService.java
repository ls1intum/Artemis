package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.domain.StudentScore;
import de.tum.cit.aet.artemis.assessment.domain.TeamScore;
import de.tum.cit.aet.artemis.assessment.dto.score.PeerExerciseAverageDTO;
import de.tum.cit.aet.artemis.assessment.dto.score.StudentScoreAverageDTO;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseLatestDueDateDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExercisePeerComparisonDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExercisePeerMetadataDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExercisePeerScoreDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresAggregatedInformation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseScoresDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;

/** Computes legacy and similar-student exercise statistics from maintained participant scores. */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseScoresChartService {

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    private final ExerciseRepository exerciseRepository;

    private final ParticipationRepository participationRepository;

    public ExerciseScoresChartService(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, ParticipantScoreRepository participantScoreRepository,
            ExerciseRepository exerciseRepository, ParticipationRepository participationRepository) {
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.exerciseRepository = exerciseRepository;
        this.participationRepository = participationRepository;
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

    /**
     * Computes comparison groups on demand from bulk score aggregates, with no retained state or result-history loads.
     * The number of repository calls is bounded independently of the number of students and exercises.
     *
     * @param course the authorized course
     * @param user   the requesting student
     * @return own scores and sufficiently populated, anonymous peer aggregates
     */
    public ExercisePeerComparisonDTO getPeerComparison(Course course, User user) {
        long courseId = course.getId();
        ZonedDateTime now = ZonedDateTime.now();
        var candidates = exerciseRepository.findMetadataForPeerComparison(courseId, now, course.isOnlineCourse(), user.getLogin());
        Set<Long> extensionIds = candidates.stream().filter(exercise -> exercise.assessmentDueDate() != null || exercise.dueDate() != null).map(ExercisePeerMetadataDTO::id)
                .collect(Collectors.toSet());
        Map<Long, ZonedDateTime> latestDueDates = extensionIds.isEmpty() ? Map.of()
                : participationRepository.findLatestIndividualDueDates(extensionIds).stream()
                        .collect(Collectors.toMap(ExerciseLatestDueDateDTO::exerciseId, ExerciseLatestDueDateDTO::dueDate));
        // Submission extensions can outlast the assessment deadline, so every applicable cutoff must have passed.
        var exercises = candidates.stream().filter(exercise -> extensionIds.contains(exercise.id()))
                .filter(exercise -> exercise.dueDate() == null || now.isAfter(exercise.dueDate()))
                .filter(exercise -> exercise.assessmentDueDate() == null || now.isAfter(exercise.assessmentDueDate()))
                .filter(exercise -> !latestDueDates.containsKey(exercise.id()) || now.isAfter(latestDueDates.get(exercise.id()))).toList();
        if (exercises.isEmpty()) {
            return new ExercisePeerComparisonDTO(List.of(), 0, 0);
        }
        Set<Long> exerciseIds = exercises.stream().map(ExercisePeerMetadataDTO::id).collect(Collectors.toSet());
        Set<ExercisePeerMetadataDTO> individualExercises = exercises.stream().filter(exercise -> exercise.mode() == ExerciseMode.INDIVIDUAL).collect(Collectors.toSet());
        Set<ExercisePeerMetadataDTO> teamExercises = exercises.stream().filter(exercise -> exercise.mode() == ExerciseMode.TEAM).collect(Collectors.toSet());
        Set<Long> groupingIds = exercises.stream().filter(exercise -> exercise.includedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map(ExercisePeerMetadataDTO::id).collect(Collectors.toSet());
        Map<Long, double[]> totals = new HashMap<>();
        if (!groupingIds.isEmpty()) {
            List<StudentScoreAverageDTO> aggregates = new ArrayList<>();
            if (individualExercises.stream().anyMatch(exercise -> groupingIds.contains(exercise.id()))) {
                aggregates.addAll(studentScoreRepository.findStudentAveragesForComparison(courseId, groupingIds));
            }
            if (teamExercises.stream().anyMatch(exercise -> groupingIds.contains(exercise.id()))) {
                aggregates.addAll(teamScoreRepository.findStudentAveragesForComparison(courseId, groupingIds));
            }
            for (var aggregate : aggregates) {
                double[] total = totals.computeIfAbsent(aggregate.studentId(), key -> new double[2]);
                total[0] += aggregate.scoreSum();
                total[1] += aggregate.scoreCount();
            }
        }
        Map<Long, Double> averages = new HashMap<>();
        totals.forEach((id, total) -> {
            if (total[1] > 0) {
                averages.put(id, total[0] / total[1]);
            }
        });
        var cohort = SimilarStudentCohort.select(averages, user.getId());
        Map<Long, PeerExerciseAverageDTO> peerScores = new HashMap<>();
        if (!cohort.studentIds().isEmpty()) {
            if (!individualExercises.isEmpty()) {
                studentScoreRepository.findPeerExerciseAverages(exerciseIds, cohort.studentIds()).forEach(score -> peerScores.put(score.exerciseId(), score));
            }
            if (!teamExercises.isEmpty()) {
                teamScoreRepository.findPeerExerciseAverages(exerciseIds, cohort.studentIds(), user.getId()).forEach(score -> peerScores.put(score.exerciseId(), score));
            }
        }
        Map<Long, Double> ownScores = new HashMap<>();
        if (!individualExercises.isEmpty()) {
            studentScoreRepository.findOwnRatedScores(exerciseIds, user.getId()).forEach(score -> ownScores.put(score.exerciseId(), score.score()));
        }
        if (!teamExercises.isEmpty()) {
            teamScoreRepository.findOwnRatedScores(exerciseIds, user.getId()).forEach(score -> ownScores.put(score.exerciseId(), score.score()));
        }
        var rows = exercises.stream().map(exercise -> {
            Double own = ownScores.get(exercise.id());
            Double ownScore = own != null ? roundScoreSpecifiedByCourseSettings(own, course) : null;
            PeerExerciseAverageDTO peer = peerScores.get(exercise.id());
            boolean sufficient = peer != null && peer.participantCount() >= SimilarStudentCohort.MINIMUM_PEERS;
            return new ExercisePeerScoreDTO(exercise.id(), exercise.title(), exercise.type(), exercise.releaseDate(), ownScore,
                    sufficient ? roundScoreSpecifiedByCourseSettings(peer.average(), course) : null, sufficient ? peer.participantCount() : 0);
        }).toList();
        return new ExercisePeerComparisonDTO(rows, cohort.groupCount(), cohort.studentIds().size());
    }

    private Map<Long, TeamScore> getScoreOfStudentForTeamExercises(User user, Set<Exercise> teamExercises) {
        if (teamExercises.isEmpty()) {
            return Map.of();
        }
        return teamScoreRepository.findAllByExerciseAndUserWithEagerExercise(teamExercises, user).stream()
                .collect(Collectors.toMap(teamScore -> teamScore.getExercise().getId(), teamScore -> teamScore));
    }

    private Map<Long, StudentScore> getScoreOfStudentForIndividualExercises(User user, Set<Exercise> individualExercises) {
        if (individualExercises.isEmpty()) {
            return Map.of();
        }
        return studentScoreRepository.findAllByExerciseAndUserWithEagerExercise(individualExercises, user).stream()
                .collect(Collectors.toMap(studentScore -> studentScore.getExercise().getId(), studentSore -> studentSore));
    }

    private ExerciseScoresDTO createExerciseScoreDTO(Map<Long, ExerciseScoresAggregatedInformation> exerciseIdToAggregatedInformation,
            Map<Long, StudentScore> individualExerciseIdToStudentScore, Map<Long, TeamScore> teamExerciseIdToTeamScore, Exercise exercise) {
        ExerciseScoresAggregatedInformation aggregatedInformation = exerciseIdToAggregatedInformation.get(exercise.getId());

        Course course = exercise.getCourseViaExerciseGroupOrCourseMemberElseThrow();

        double averageScoreAchieved = 0D;
        double maxScoreAchieved = 0D;

        if (aggregatedInformation != null && aggregatedInformation.averageScoreAchieved() != null && aggregatedInformation.maxScoreAchieved() != null) {
            averageScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.averageScoreAchieved(), course);
            maxScoreAchieved = roundScoreSpecifiedByCourseSettings(aggregatedInformation.maxScoreAchieved(), course);
        }

        ParticipantScore participantScore = exercise.getMode().equals(ExerciseMode.INDIVIDUAL) ? individualExerciseIdToStudentScore.get(exercise.getId())
                : teamExerciseIdToTeamScore.get(exercise.getId());
        final double scoreOfStudent = participantScore == null || participantScore.getLastRatedScore() == null ? 0D
                : roundScoreSpecifiedByCourseSettings(participantScore.getLastRatedScore(), course);

        return ExerciseScoresDTO.of(exercise, scoreOfStudent, averageScoreAchieved, maxScoreAchieved);
    }
}
