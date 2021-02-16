package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.service.util.RoundingUtil.round;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.CourseScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;

@RestController
@RequestMapping("/api")
public class ParticipantScoreResource {

    private final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

    private final ParticipantScoreRepository participantScoreRepository;

    private final StudentScoreRepository studentScoreRepository;

    private final TeamScoreRepository teamScoreRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public ParticipantScoreResource(StudentScoreRepository studentScoreRepository, TeamScoreRepository teamScoreRepository, AuthorizationCheckService authorizationCheckService,
            CourseRepository courseRepository, ExamRepository examRepository, ParticipantScoreRepository participantScoreRepository, UserRepository userRepository) {
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.studentScoreRepository = studentScoreRepository;
        this.teamScoreRepository = teamScoreRepository;
        this.participantScoreRepository = participantScoreRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/courses/{courseId}/course-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<CourseScoreDTO>> getCourseScoresOfCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get course scores for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> visibleExercisesThatCount = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> exercise.getReleaseDate() == null || exercise.getReleaseDate().isBefore(ZonedDateTime.now()))
                .filter(exercise -> exercise.getIncludedInOverallScore() != IncludedInOverallScore.NOT_INCLUDED).collect(Collectors.toSet());

        // this is the denominator when we calculate the achieved score of a student
        Double regularAchievablePoints = visibleExercisesThatCount.stream().filter(exercise -> exercise.getIncludedInOverallScore() == IncludedInOverallScore.INCLUDED_COMPLETELY)
                .map(Exercise::getMaxPoints).reduce(0.0, Double::sum);
        // 0.0 means we can not reasonably calculate the achieved points / scores
        if (regularAchievablePoints.equals(0.0)) {
            return ResponseEntity.ok().body(List.of());
        }

        Set<Exercise> visibleIndividualExercisesThatCount = visibleExercisesThatCount.stream().filter(exercise -> !exercise.isTeamMode()).collect(Collectors.toSet());
        Set<Exercise> visibleTeamExercisesThatCount = visibleExercisesThatCount.stream().filter(exercise -> exercise.isTeamMode()).collect(Collectors.toSet());

        List<User> studentOfCourse = userRepository.findAllInGroupWithAuthorities(course.getStudentGroupName());
        // For every student in the course we want to calculate course score
        Map<Long, CourseScoreDTO> studentIdToCourseScore = studentOfCourse.stream().collect(Collectors.toMap(User::getId, user -> new CourseScoreDTO(user)));

        // individual exercises
        // [0] -> User
        // [1] -> sum of achieved points in exercises
        List<Object[]> studentAndAchievedPoints = studentScoreRepository.getAchievedPointsOfStudents(visibleIndividualExercisesThatCount);
        for (Object[] rawData : studentAndAchievedPoints) {
            User user = (User) rawData[0];
            double achievedPoints = rawData[1] != null ? ((Number) rawData[1]).doubleValue() : 0.0;
            if (studentIdToCourseScore.containsKey(user.getId())) {
                studentIdToCourseScore.get(user.getId()).pointsAchieved += achievedPoints;
            }
        }

        // team exercises
        // [0] -> Team
        // [1] -> sum of achieved points in exercises
        List<Object[]> teamAndAchievedPoints = teamScoreRepository.getAchievedPointsOfTeams(visibleTeamExercisesThatCount);
        for (Object[] rawData : teamAndAchievedPoints) {
            Team team = (Team) rawData[0];
            double achievedPoints = rawData[1] != null ? ((Number) rawData[1]).doubleValue() : 0.0;
            for (User student : team.getStudents()) {
                if (studentIdToCourseScore.containsKey(student.getId())) {
                    studentIdToCourseScore.get(student.getId()).pointsAchieved += achievedPoints;
                }
            }
        }

        // calculating achieved score
        for (CourseScoreDTO courseScoreDTO : studentIdToCourseScore.values()) {
            courseScoreDTO.scoreAchieved = round((courseScoreDTO.pointsAchieved / regularAchievablePoints) * 100.0);
            courseScoreDTO.regularPointsAchievable = regularAchievablePoints;
        }
        log.info("getCourseScoresOfCourse took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(new ArrayList<>(studentIdToCourseScore.values()));
    }

    /**
     * GET /courses/:courseId/participant-scores  gets the participant scores of the course
     *
     * @param courseId   the id of the course for which to get the participant score
     * @param pageable   pageable object
     * @param getUnpaged if set all participant scores of the course will be loaded (paging deactivated)
     * @return the ResponseEntity with status 200 (OK) and with the participant scores in the body
     */
    @GetMapping("/courses/{courseId}/participant-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfCourse(@PathVariable Long courseId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get participant scores for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise).collect(Collectors.toSet());
        if (getUnpaged) {
            pageable = Pageable.unpaged();
        }
        List<ParticipantScoreDTO> resultsOfAllExercises = gertParticipantScoreDTOs(pageable, exercisesOfCourse);
        log.info("getParticipantScoresOfCourse took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(resultsOfAllExercises);
    }

    /**
     * GET /courses/:courseId/participant-scores/average-participant  gets the average scores of the participants in the course
     * <p>
     * Important: Exercises with {@link de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore#NOT_INCLUDED} will be not taken into account!
     *
     * @param courseId the id of the course for which to get the average scores of the participants
     * @return the ResponseEntity with status 200 (OK) and with the average scores in the body
     */
    @GetMapping("/courses/{courseId}/participant-scores/average-participant")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreAverageDTO>> getAverageScoreOfParticipantInCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get average participant scores of participants for course : {}", courseId);
        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());

        List<ParticipantScoreAverageDTO> resultsOfAllExercises = getParticipantScoreAverageDTOs(exercisesOfCourse);
        log.info("getAverageScoreOfStudentInCourse took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(resultsOfAllExercises);
    }

    /**
     * GET /courses/:courseId/participant-scores/average  gets the average score of the course
     * <p>
     * Important: Exercises with {@link de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore#NOT_INCLUDED} will be not taken into account!
     *
     * @param courseId                the id of the course for which to get the average score
     * @param onlyConsiderRatedScores if set the method will get the rated average score, if unset the method will get the average score
     * @return the ResponseEntity with status 200 (OK) and with average score in the body
     */
    @GetMapping("/courses/{courseId}/participant-scores/average")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Long> getAverageScoreOfCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores) {
        long start = System.currentTimeMillis();
        if (onlyConsiderRatedScores) {
            log.debug("REST request to get average rated scores for course : {}", courseId);
        }
        else {
            log.debug("REST request to get average scores for course : {}", courseId);
        }

        Course course = courseRepository.findWithEagerExercisesById(courseId);
        if (course == null) {
            return notFound();
        }
        if (!authorizationCheckService.isAtLeastInstructorInCourse(course, null)) {
            return forbidden();
        }
        Set<Exercise> includedExercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(Collectors.toSet());

        Long averageScore = getAverageScore(onlyConsiderRatedScores, includedExercisesOfCourse);

        log.info("getAverageScoreOfCourse took " + (System.currentTimeMillis() - start) + "ms");

        return ResponseEntity.ok().body(averageScore);
    }

    /**
     * GET /exams/:examId/participant-scores  gets the participant scores of the exam
     *
     * @param examId     the id of the exam for which to get the participant score
     * @param pageable   pageable object
     * @param getUnpaged if set all participant scores of the exam will be loaded (paging deactivated)
     * @return the ResponseEntity with status 200 (OK) and with the participant scores in the body
     */
    @GetMapping("/exams/{examId}/participant-scores")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfExam(@PathVariable Long examId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get participant scores for exam : {}", examId);
        Optional<Exam> examOptional = examRepository.findWithExerciseGroupsAndExercisesById(examId);
        if (examOptional.isEmpty()) {
            return notFound();
        }
        Exam exam = examOptional.get();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfExam = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exercisesOfExam.addAll(exerciseGroup.getExercises());
        }
        if (getUnpaged) {
            pageable = Pageable.unpaged();
        }

        List<ParticipantScoreDTO> resultsOfAllExercises = gertParticipantScoreDTOs(pageable, exercisesOfExam);
        log.info("getParticipantScoresOfExam took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(resultsOfAllExercises);
    }

    /**
     * GET /exams/:examId/participant-scores/average gets the average score of the exam
     * <p>
     * Important: Exercises with {@link de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore#NOT_INCLUDED} will be not taken into account!
     *
     * @param examId                  the id of the exam for which to get the average score
     * @param onlyConsiderRatedScores if set the method will get the rated average score, if unset the method will get the average score
     * @return the ResponseEntity with status 200 (OK) and with average score in the body
     */
    @GetMapping("/exams/{examId}/participant-scores/average")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Long> getAverageScoreOfExam(@PathVariable Long examId, @RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores) {
        long start = System.currentTimeMillis();
        if (onlyConsiderRatedScores) {
            log.debug("REST request to get average rated scores for exam : {}", examId);
        }
        else {
            log.debug("REST request to get average scores for exam : {}", examId);
        }

        Optional<Exam> examOptional = examRepository.findWithExerciseGroupsAndExercisesById(examId);
        if (examOptional.isEmpty()) {
            return notFound();
        }
        Exam exam = examOptional.get();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfExam = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exercisesOfExam.addAll(exerciseGroup.getExercises());
        }
        Set<Exercise> includedExercisesOfExam = exercisesOfExam.stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .collect(Collectors.toSet());

        Long averageScore = getAverageScore(onlyConsiderRatedScores, includedExercisesOfExam);

        log.info("getAverageScoreOfExam took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(averageScore);
    }

    /**
     * GET /exams/:examId/participant-scores/average-participant  gets the average scores of the participants in the exam
     * <p>
     * Important: Exercises with {@link de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore#NOT_INCLUDED} will be not taken into account!
     *
     * @param examId the id of the exam for which to get the average scores of the participants
     * @return the ResponseEntity with status 200 (OK) and with the average scores in the body
     */
    @GetMapping("/exams/{examId}/participant-scores/average-participant")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<ParticipantScoreAverageDTO>> getAverageScoreOfParticipantInExam(@PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get average participant scores of participants for exam : {}", examId);
        Optional<Exam> examOptional = examRepository.findWithExerciseGroupsAndExercisesById(examId);
        if (examOptional.isEmpty()) {
            return notFound();
        }
        Exam exam = examOptional.get();
        if (!authorizationCheckService.isAtLeastInstructorInCourse(exam.getCourse(), null)) {
            return forbidden();
        }
        Set<Exercise> exercisesOfExam = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exercisesOfExam.addAll(exerciseGroup.getExercises());
        }
        Set<Exercise> includedExercisesOfExam = exercisesOfExam.stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED))
                .collect(Collectors.toSet());

        List<ParticipantScoreAverageDTO> resultsOfAllExercises = getParticipantScoreAverageDTOs(includedExercisesOfExam);
        log.info("getAverageScoreOfParticipantInExam took " + (System.currentTimeMillis() - start) + "ms");
        return ResponseEntity.ok().body(resultsOfAllExercises);
    }

    private List<ParticipantScoreDTO> gertParticipantScoreDTOs(Pageable pageable, Set<Exercise> exercises) {
        Set<Exercise> individualExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercisesOfCourse = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreDTO> resultsIndividualExercises = studentScoreRepository.findAllByExerciseIn(individualExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).collect(Collectors.toList());
        List<ParticipantScoreDTO> resultsTeamExercises = teamScoreRepository.findAllByExerciseIn(teamExercisesOfCourse, pageable).stream()
                .map(ParticipantScoreDTO::generateFromParticipantScore).collect(Collectors.toList());
        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).collect(Collectors.toList());
    }

    private List<ParticipantScoreAverageDTO> getParticipantScoreAverageDTOs(Set<Exercise> exercises) {
        Set<Exercise> individualExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.INDIVIDUAL)).collect(Collectors.toSet());
        Set<Exercise> teamExercises = exercises.stream().filter(exercise -> exercise.getMode().equals(ExerciseMode.TEAM)).collect(Collectors.toSet());

        List<ParticipantScoreAverageDTO> resultsIndividualExercises = studentScoreRepository.getAvgScoreOfStudentsInExercises(individualExercises);
        List<ParticipantScoreAverageDTO> resultsTeamExercises = teamScoreRepository.getAvgScoreOfTeamInExercises(teamExercises);

        return Stream.concat(resultsIndividualExercises.stream(), resultsTeamExercises.stream()).collect(Collectors.toList());
    }

    private Long getAverageScore(@RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores, Set<Exercise> includedExercises) {
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
