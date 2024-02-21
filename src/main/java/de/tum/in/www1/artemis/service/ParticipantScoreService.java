package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.scores.ParticipantScore;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.repository.TeamScoreRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.score.ScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.score.StudentScoreSumDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ParticipantScoreService {

    private final UserRepository userRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final GradingScaleService gradingScaleService;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    private final TeamRepository teamRepository;

    public ParticipantScoreService(UserRepository userRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            GradingScaleService gradingScaleService, PresentationPointsCalculationService presentationPointsCalculationService, TeamRepository teamRepository) {
        this.userRepository = userRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.gradingScaleService = gradingScaleService;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
        this.teamRepository = teamRepository;
    }

    /**
     * This method represents a server based way to calculate a students achieved points / score in an exam.
     * <p>
     * Currently, both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in exam-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.in.www1.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param exam the exam with registered students, exercise groups and exercises for which to calculate the scores
     * @return list of scores for every registered student
     */
    public List<ScoreDTO> calculateExamScores(Exam exam) {
        if (exam == null || exam.getExerciseGroups() == null) {
            throw new IllegalArgumentException();
        }
        Set<Exercise> exercisesOfExam = new HashSet<>();
        exam.getExerciseGroups().stream().map(ExerciseGroup::getExercises).forEach(exercisesOfExam::addAll);
        Set<Exercise> includedExercises = exercisesOfExam.stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .collect(Collectors.toSet());

        Set<User> registeredUsers = exam.getRegisteredUsers();

        return calculateScores(includedExercises, registeredUsers, (double) exam.getExamMaxPoints(), 0.0, null);
    }

    /**
     * This method represents a server based way to calculate a students achieved points / score in a course.
     * <p>
     * Currently, both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in course-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.in.www1.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param course the course with exercises for which to calculate the course scores
     * @return list of course scores for every member of the course
     */
    public List<ScoreDTO> calculateCourseScores(Course course) {
        if (course == null || course.getExercises() == null) {
            throw new IllegalArgumentException();
        }

        // we want the score for everybody who can perform exercises in the course (students, tutors and instructors)
        Set<User> usersOfCourse = new HashSet<>();
        usersOfCourse.addAll(userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getStudentGroupName()));
        usersOfCourse.addAll(userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getTeachingAssistantGroupName()));
        usersOfCourse.addAll(userRepository.findAllWithGroupsAndAuthoritiesByIsDeletedIsFalseAndGroupsContains(course.getInstructorGroupName()));

        // we only consider released exercises that are not optional
        Set<Exercise> exercisesToConsider = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(ZonedDateTime.now()))
                .filter(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED).collect(Collectors.toSet());

        // this is the denominator when we calculate the achieved score of a student
        double regularAchievablePoints = exercisesToConsider.stream().filter(exercise -> exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY)
                .mapToDouble(Exercise::getMaxPoints).sum();
        GradingScale gradingScale = gradingScaleService.findGradingScaleByCourseId(course.getId()).orElse(null);

        // calculates the achievable presentation points that need to be added to the regular achievable points
        double achievablePresentationPoints = presentationPointsCalculationService.calculateReachablePresentationPoints(gradingScale, regularAchievablePoints);
        regularAchievablePoints += achievablePresentationPoints;

        return calculateScores(exercisesToConsider, usersOfCourse, regularAchievablePoints, achievablePresentationPoints, gradingScale);
    }

    private List<ScoreDTO> calculateScores(Set<Exercise> exercises, Set<User> users, Double scoreCalculationDenominator, double achievablePresentationPoints,
            GradingScale gradingScale) {
        // 0.0 means we can not reasonably calculate the achieved points / scores
        if (scoreCalculationDenominator.equals(0.0)) {
            return List.of();
        }

        Set<Exercise> individualExercises = exercises.stream().filter(Predicate.not(Exercise::isTeamMode)).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(Exercise::isTeamMode).collect(Collectors.toSet());

        Course course = exercises.stream().findAny().orElseThrow(() -> new EntityNotFoundException("The result you are referring to does not exist"))
                .getCourseViaExerciseGroupOrCourseMember();

        // individual exercises
        final var studentAndAchievedPoints = studentScoreRepository.getAchievedPointsOfStudents(individualExercises);
        Map<Long, Double> pointsAchieved = studentAndAchievedPoints.stream().collect(Collectors.toMap(StudentScoreSumDTO::userId, StudentScoreSumDTO::sumPointsAchieved));

        // team exercises
        // [0] -> Team ID
        // [1] -> sum of achieved points in exercises
        // We have to retrieve this separately because the students are not directly retrievable due to the taxonomy structure
        List<Object[]> teamAndAchievedPoints = teamScoreRepository.getAchievedPointsOfTeams(teamExercises);
        List<Long> teamIds = teamAndAchievedPoints.stream().map(rawData -> ((Long) rawData[0])).toList();
        Map<Long, Team> teamIdToTeam = teamRepository.findAllWithStudentsByIdIn(teamIds).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        final Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        for (Object[] rawData : teamAndAchievedPoints) {
            Team team = teamIdToTeam.get((Long) rawData[0]);
            double achievedPoints = rawData[1] != null ? ((Number) rawData[1]).doubleValue() : 0.0;
            for (User student : team.getStudents()) {
                if (userIds.contains(student.getId())) {
                    pointsAchieved.put(student.getId(), pointsAchieved.getOrDefault(student.getId(), 0.0) + achievedPoints);
                }
            }
        }

        // add presentationPoints to pointsAchieved
        presentationPointsCalculationService.addPresentationPointsToPointsAchieved(gradingScale, pointsAchieved, achievablePresentationPoints);

        // calculating achieved scores
        Map<Long, Double> scoreAchieved = pointsAchieved.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> roundScoreSpecifiedByCourseSettings(entry.getValue() / scoreCalculationDenominator * 100.0, course)));

        return users.stream()
                .map(user -> ScoreDTO.of(user, pointsAchieved.getOrDefault(user.getId(), 0D), scoreAchieved.getOrDefault(user.getId(), 0D), scoreCalculationDenominator))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Gets all participation scores of the exercises for a user.
     *
     * @param user      the user whose scores should be fetched
     * @param exercises the exercises the scores should be fetched from
     * @return stream of participant scores
     */
    public Stream<ParticipantScore> getStudentAndTeamParticipations(User user, Set<Exercise> exercises) {
        var studentScores = studentScoreRepository.findAllByExercisesAndUser(exercises, user);
        var teamScores = teamScoreRepository.findAllByExercisesAndUser(exercises, user);
        return Stream.concat(studentScores.stream(), teamScores.stream());
    }

    /**
     * Gets all participation scores of the exercises for a user.
     *
     * @param user      the user whose scores should be fetched
     * @param exercises the exercises the scores should be fetched from
     * @return stream of participant latest scores
     */
    public Stream<Double> getStudentAndTeamParticipationScores(User user, Set<Exercise> exercises) {
        return getStudentAndTeamParticipations(user, exercises).map(ParticipantScore::getLastScore);
    }

    /**
     * Gets all participation scores of the exercises for a user.
     *
     * @param user      the user whose scores should be fetched
     * @param exercises the exercises the scores should be fetched from
     * @return stream of participant latest scores
     */
    public DoubleStream getStudentAndTeamParticipationScoresAsDoubleStream(User user, Set<Exercise> exercises) {
        return getStudentAndTeamParticipationScores(user, exercises).mapToDouble(Double::doubleValue);
    }
}
