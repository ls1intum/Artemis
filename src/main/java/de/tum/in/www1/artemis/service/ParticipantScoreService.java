package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.repository.ParticipantScoreRepository;
import de.tum.in.www1.artemis.repository.StudentScoreRepository;
import de.tum.in.www1.artemis.repository.TeamScoreRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.web.rest.dto.CourseScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;

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
     * This method represents a server based way to calculate a students achieved points / score in a course.
     * <p>
     * Currently both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in course-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.in.www1.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param course the course with exercises for which to calculate the course scores
     * @return list of course scores for every member of the course
     */
    public List<CourseScoreDTO> getCourseScoreDTOs(Course course) {
        if (course == null || course.getExercises() == null) {
            throw new IllegalArgumentException();
        }

        // we want the score for everybody who can perform exercises in the course (students, tutors and instructors)
        List<User> usersOfCourse = new ArrayList<>();
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getStudentGroupName()));
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getTeachingAssistantGroupName()));
        usersOfCourse.addAll(userRepository.findAllInGroupWithAuthorities(course.getInstructorGroupName()));

        // we only consider released exercises that are not optional
        Set<Exercise> exercisesToConsider = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(ZonedDateTime.now()))
                .filter(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED).collect(Collectors.toSet());

        return calculateCourseScoreDTOs(exercisesToConsider, usersOfCourse);
    }

    private List<CourseScoreDTO> calculateCourseScoreDTOs(Set<Exercise> exercises, List<User> users) {
        // this is the denominator when we calculate the achieved score of a student
        Double regularAchievablePoints = exercises.stream().filter(exercise -> exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map(Exercise::getMaxPoints).reduce(0.0, Double::sum);

        // 0.0 means we can not reasonably calculate the achieved points / scores
        if (regularAchievablePoints.equals(0.0)) {
            return List.of();
        }

        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(Exercise::isTeamMode).collect(Collectors.toSet());

        // For every student we want to calculate the score
        Map<Long, CourseScoreDTO> userIdToScores = users.stream().collect(Collectors.toMap(User::getId, CourseScoreDTO::new));

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
        for (CourseScoreDTO scoreDTO : userIdToScores.values()) {
            scoreDTO.scoreAchieved = round((scoreDTO.pointsAchieved / regularAchievablePoints) * 100.0);
            scoreDTO.regularPointsAchievable = regularAchievablePoints;
        }

        return new ArrayList<>(userIdToScores.values());

    }

    /**
     * Gets all the participant scores that exist for given exercises and converts them to the corresponding DTOs
     *
     * @param pageable  pageable object to specify paging
     * @param exercises exercises for which to get all the participant scores
     * @return all participant scores of the exercises converted to DTOs
     */
    public List<ParticipantScoreDTO> getParticipantScoreDTOs(Pageable pageable, Set<Exercise> exercises) {
        Set<Exercise> individualExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreDTO> resultsIndividualExercises = studentScoreRepository.findAllByExerciseIn(individualExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).collect(Collectors.toList());
        List<ParticipantScoreDTO> resultsTeamExercises = teamScoreRepository.findAllByExerciseIn(teamExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).collect(Collectors.toList());
        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).collect(Collectors.toList());
    }

    /**
     * Calculates various average statistics for every user / team that participated in the given exercises
     *
     * @param exercises exercises for which to calcualte the statistics
     * @return DTOs containing the statistics for every user / team
     */
    public List<ParticipantScoreAverageDTO> getParticipantScoreAverageDTOs(Set<Exercise> exercises) {
        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreAverageDTO> resultsIndividualExercises = studentScoreRepository.getAvgScoreOfStudentsInExercises(individualExercises);
        List<ParticipantScoreAverageDTO> resultsTeamExercises = teamScoreRepository.getAvgScoreOfTeamInExercises(teamExercises);

        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).collect(Collectors.toList());
    }

    /**
     * Calculated the average last score or average last rated score achieved in the given exercises
     *
     * @param onlyConsiderRatedScores consider either the last score or the last rated score
     * @param includedExercises       exercises to include in the average calculation
     * @return average last score or average last rated score achieved in the given exercises
     */
    public Long getAverageScore(@RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores, Set<Exercise> includedExercises) {
        Long averageScore;
        if (onlyConsiderRatedScores) {
            averageScore = participantScoreRepository.findAvgRatedScore(includedExercises);
        }
        else {
            averageScore = participantScoreRepository.findAvgScore(includedExercises);
        }
        return averageScore;
    }

}
