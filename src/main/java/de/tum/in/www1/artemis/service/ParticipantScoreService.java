package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.roundScoreSpecifiedByCourseSettings;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

@Service
public class ParticipantScoreService {

    private final UserRepository userRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final ParticipantScoreRepository participantScoreRepository;

    public ParticipantScoreService(UserRepository userRepository, StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository,
            ParticipantScoreRepository participantScoreRepository) {
        this.userRepository = userRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participantScoreRepository = participantScoreRepository;
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

        return calculateScores(includedExercises, exam.getRegisteredUsers(), (double) exam.getMaxPoints());
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
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getStudentGroupName()));
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()));
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName()));

        // we only consider released exercises that are not optional
        Set<Exercise> exercisesToConsider = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(ZonedDateTime.now()))
                .filter(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED).collect(Collectors.toSet());

        // this is the denominator when we calculate the achieved score of a student
        Double regularAchievablePoints = exercisesToConsider.stream().filter(exercise -> exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map(Exercise::getMaxPoints).reduce(0.0, Double::sum);

        return calculateScores(exercisesToConsider, usersOfCourse, regularAchievablePoints);
    }

    private List<ScoreDTO> calculateScores(Set<Exercise> exercises, Set<User> users, Double scoreCalculationDenominator) {
        // 0.0 means we can not reasonably calculate the achieved points / scores
        if (scoreCalculationDenominator.equals(0.0)) {
            return List.of();
        }

        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(Exercise::isTeamMode).collect(Collectors.toSet());

        Course course = exercises.stream().findAny().get().getCourseViaExerciseGroupOrCourseMember();

        // For every student we want to calculate the score
        Map<Long, ScoreDTO> userIdToScores = users.stream().collect(Collectors.toMap(User::getId, ScoreDTO::new));

        // individual exercises
        // [0] -> User
        // [1] -> sum of achieved points in exercises
        List<Object[]> studentAndAchievedPoints = studentScoreRepository.getAchievedPointsOfStudents(individualExercises);
        for (Object[] rawData : studentAndAchievedPoints) {
            User user = (User) rawData[0];
            double achievedPoints = rawData[1] != null ? ((Number) rawData[1]).doubleValue() : 0.0;
            if (userIdToScores.containsKey(user.getId())) {
                userIdToScores.get(user.getId()).pointsAchieved += achievedPoints;
            }
        }

        // team exercises
        // [0] -> Team
        // [1] -> sum of achieved points in exercises
        List<Object[]> teamAndAchievedPoints = teamScoreRepository.getAchievedPointsOfTeams(teamExercises);
        for (Object[] rawData : teamAndAchievedPoints) {
            Team team = (Team) rawData[0];
            double achievedPoints = rawData[1] != null ? ((Number) rawData[1]).doubleValue() : 0.0;
            for (User student : team.getStudents()) {
                if (userIdToScores.containsKey(student.getId())) {
                    userIdToScores.get(student.getId()).pointsAchieved += achievedPoints;
                }
            }
        }

        // calculating achieved score
        for (ScoreDTO scoreDTO : userIdToScores.values()) {
            scoreDTO.scoreAchieved = roundScoreSpecifiedByCourseSettings((scoreDTO.pointsAchieved / scoreCalculationDenominator) * 100.0, course);
            // sending this for debugging purposes to find out why the scores' calculation could be wrong
            scoreDTO.regularPointsAchievable = scoreCalculationDenominator;
        }

        return new ArrayList<>(userIdToScores.values());

    }

    /**
     * Gets all the participant scores that exist for given exercises and converts them to the corresponding DTOs
     *
     * @param pageable  pageable object to specify paging
     * @param exercises exercises for which to get all the participant scores
     * @return an unmodifiable list of all participant scores of the exercises converted to DTOs
     */
    public List<ParticipantScoreDTO> getParticipantScoreDTOs(Pageable pageable, Set<Exercise> exercises) {
        Set<Exercise> individualExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreDTO> resultsIndividualExercises = studentScoreRepository.findAllByExerciseIn(individualExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).toList();
        List<ParticipantScoreDTO> resultsTeamExercises = teamScoreRepository.findAllByExerciseIn(teamExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).toList();
        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).toList();
    }

    /**
     * Calculates various average statistics for every user / team that participated in the given exercises
     *
     * @param exercises exercises for which to calculate the statistics
     * @return an unmodifiable list of DTOs containing the statistics for every user / team
     */
    public List<ParticipantScoreAverageDTO> getParticipantScoreAverageDTOs(Set<Exercise> exercises) {
        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreAverageDTO> resultsIndividualExercises = studentScoreRepository.getAvgScoreOfStudentsInExercises(individualExercises);
        List<ParticipantScoreAverageDTO> resultsTeamExercises = teamScoreRepository.getAvgScoreOfTeamInExercises(teamExercises);

        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).toList();
    }

    /**
     * Calculated the average last score or average last rated score achieved in the given exercises
     *
     * @param onlyConsiderRatedScores consider either the last score or the last rated score
     * @param includedExercises       exercises to include in the average calculation
     * @return average last score or average last rated score achieved in the given exercises
     */
    public Double getAverageScore(@RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores, Set<Exercise> includedExercises) {
        Double averageScore;
        if (onlyConsiderRatedScores) {
            averageScore = participantScoreRepository.findAvgRatedScore(includedExercises);
        }
        else {
            averageScore = participantScoreRepository.findAvgScore(includedExercises);
        }
        return averageScore;
    }

}
