package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

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

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.ParticipantScore;
import de.tum.cit.aet.artemis.assessment.listener.ResultListener;
import de.tum.cit.aet.artemis.assessment.repository.ParticipantScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.StudentScoreRepository;
import de.tum.cit.aet.artemis.assessment.repository.TeamScoreRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.web.rest.dto.score.ScoreDTO;
import de.tum.cit.aet.artemis.web.rest.dto.score.StudentScoreSum;
import de.tum.cit.aet.artemis.web.rest.dto.score.TeamScoreSum;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

@Profile(PROFILE_CORE)
@Service
public class ParticipantScoreService {

    private final UserRepository userRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final GradingScaleService gradingScaleService;

    private final PresentationPointsCalculationService presentationPointsCalculationService;

    private final TeamRepository teamRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public ParticipantScoreService(UserRepository userRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            GradingScaleService gradingScaleService, PresentationPointsCalculationService presentationPointsCalculationService, TeamRepository teamRepository,
            ParticipantScoreRepository participantScoreRepository) {
        this.userRepository = userRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.gradingScaleService = gradingScaleService;
        this.presentationPointsCalculationService = presentationPointsCalculationService;
        this.teamRepository = teamRepository;
        this.participantScoreRepository = participantScoreRepository;
    }

    /**
     * This method represents a server based way to calculate a students achieved points / score in an exam.
     * <p>
     * Currently, both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in exam-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link ResultListener}
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
     * The goal is to switch completely to this much faster server based calculation if the {@link ResultListener}
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
        final Set<StudentScoreSum> studentAndAchievedPoints = studentScoreRepository.getAchievedPointsOfStudents(individualExercises);
        Map<Long, Double> pointsAchieved = studentAndAchievedPoints.stream().collect(Collectors.toMap(StudentScoreSum::userId, StudentScoreSum::sumPointsAchieved));

        // We have to retrieve this separately because the students are not directly retrievable due to the taxonomy structure
        Set<TeamScoreSum> teamScoreSums = teamScoreRepository.getAchievedPointsOfTeams(teamExercises);
        Set<Long> teamIds = teamScoreSums.stream().map(TeamScoreSum::teamId).collect(Collectors.toSet());
        var teamList = teamRepository.findAllWithStudentsByIdIn(teamIds);
        var teamMap = teamList.stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        final Set<Long> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        for (TeamScoreSum teamScoreSum : teamScoreSums) {
            Team team = teamMap.get(teamScoreSum.teamId());
            for (User student : team.getStudents()) {
                if (userIds.contains(student.getId())) {
                    pointsAchieved.merge(student.getId(), teamScoreSum.sumPointsAchieved(), Double::sum);
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
     * Gets all achieved points of the exercises for a user.
     *
     * @param user      the user whose scores should be fetched
     * @param exercises the exercises the scores should be fetched from
     * @return stream of achieved latest points
     */
    public Stream<Double> getStudentAndTeamParticipationPoints(User user, Set<Exercise> exercises) {
        return getStudentAndTeamParticipations(user, exercises).map(ParticipantScore::getLastPoints);
    }

    /**
     * Gets all achieved points of the exercises for a user.
     *
     * @param user      the user whose scores should be fetched
     * @param exercises the exercises the scores should be fetched from
     * @return stream of achieved latest points
     */
    public DoubleStream getStudentAndTeamParticipationPointsAsDoubleStream(User user, Set<Exercise> exercises) {
        return getStudentAndTeamParticipationPoints(user, exercises).mapToDouble(Double::doubleValue);
    }

    public double getAverageOfAverageScores(Set<Exercise> exercises) {
        return participantScoreRepository.findAverageScoreForExercises(exercises).stream().mapToDouble(exerciseInfo -> (double) exerciseInfo.get("averageScore")).average()
                .orElse(0.0);
    }

    /**
     * Get all users that participated in the given exercise.
     *
     * @param exercise the exercise for which to get all users that participated
     * @return set of users that participated in the exercise
     */
    public Set<User> getAllParticipatedUsersInExercise(Exercise exercise) {
        if (exercise.isTeamMode()) {
            return teamScoreRepository.findAllUsersWithScoresByExercise(exercise);
        }
        else {
            return studentScoreRepository.findAllUsersWithScoresByExercise(exercise);
        }
    }
}
