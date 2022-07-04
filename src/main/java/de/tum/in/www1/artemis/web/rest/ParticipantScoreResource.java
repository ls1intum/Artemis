package de.tum.in.www1.artemis.web.rest;

import static java.util.stream.Collectors.toSet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipantScoreService;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreAverageDTO;
import de.tum.in.www1.artemis.web.rest.dto.ParticipantScoreDTO;
import de.tum.in.www1.artemis.web.rest.dto.ScoreDTO;

@RestController
@RequestMapping("/api")
public class ParticipantScoreResource {

    private final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final ParticipantScoreService participantScoreService;

    private final AuthorizationCheckService authorizationCheckService;

    public ParticipantScoreResource(AuthorizationCheckService authorizationCheckService, CourseRepository courseRepository, ExamRepository examRepository,
            ParticipantScoreService participantScoreService) {
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.participantScoreService = participantScoreService;
    }

    /**
     * GET /courses/:courseId/course-scores gets the course scores of the course
     * <p>
     * This method represents a server based way to calculate a students achieved points / score in a course.
     * <p>
     * Currently both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in course-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.in.www1.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param courseId the id of the course for which to calculate the course scores
     * @return list of scores for every member of the course
     */
    @GetMapping("/courses/{courseId}/course-scores")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ScoreDTO>> getScoresOfCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get course scores for course : {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        List<ScoreDTO> scoreDTOS = participantScoreService.calculateCourseScores(course);
        log.info("getScoresOfCourse took {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok().body(scoreDTOS);
    }

    /**
     * GET /exams/:examId/exam-scores gets the exam scores of the exam
     * <p>
     * This method represents a server based way to calculate a students achieved points / score in an exam.
     * <p>
     * Currently both this server based calculation method and the traditional client side calculation method is used
     * side-by-side in exam-scores.component.ts.
     * <p>
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.in.www1.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param examId the id of the exam for which to calculate the exam scores
     * @return list of scores for every registered user in the xam
     */
    @GetMapping("/exams/{examId}/exam-scores")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ScoreDTO>> getScoresOfExam(@PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get exam scores for exam : {}", examId);
        Exam exam = examRepository.findByIdWithRegisteredUsersExerciseGroupsAndExercisesElseThrow(examId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, exam.getCourse(), null);
        List<ScoreDTO> scoreDTOS = participantScoreService.calculateExamScores(exam);
        log.info("getScoresOfExam took {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok().body(scoreDTOS);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfCourse(@PathVariable Long courseId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get participant scores for course : {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Exercise> exercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise).collect(toSet());
        List<ParticipantScoreDTO> resultsOfAllExercises = participantScoreService.getParticipantScoreDTOs(getUnpaged ? Pageable.unpaged() : pageable, exercisesOfCourse);
        log.info("getParticipantScoresOfCourse took {}ms", System.currentTimeMillis() - start);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ParticipantScoreAverageDTO>> getAverageScoreOfParticipantInCourse(@PathVariable Long courseId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get average participant scores of participants for course : {}", courseId);
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Exercise> exercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(toSet());
        List<ParticipantScoreAverageDTO> resultsOfAllExercises = participantScoreService.getParticipantScoreAverageDTOs(exercisesOfCourse);
        log.info("getAverageScoreOfStudentInCourse took {}ms", System.currentTimeMillis() - start);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Double> getAverageScoreOfCourse(@PathVariable Long courseId, @RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores) {
        long start = System.currentTimeMillis();
        if (onlyConsiderRatedScores) {
            log.debug("REST request to get average rated scores for course : {}", courseId);
        }
        else {
            log.debug("REST request to get average scores for course : {}", courseId);
        }
        Course course = courseRepository.findByIdWithEagerExercisesElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Exercise> includedExercisesOfCourse = course.getExercises().stream().filter(Exercise::isCourseExercise)
                .filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(toSet());
        Double averageScore = participantScoreService.getAverageScore(onlyConsiderRatedScores, includedExercisesOfCourse);
        log.info("getAverageScoreOfCourse took {}ms", System.currentTimeMillis() - start);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ParticipantScoreDTO>> getParticipantScoresOfExam(@PathVariable Long examId, Pageable pageable,
            @RequestParam(value = "getUnpaged", required = false, defaultValue = "false") boolean getUnpaged) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get participant scores for exam : {}", examId);
        Set<Exercise> exercisesOfExam = getExercisesOfExam(examId);
        Pageable page;
        if (getUnpaged) {
            page = Pageable.unpaged();
        }
        else {
            page = pageable;
        }
        List<ParticipantScoreDTO> resultsOfAllExercises = participantScoreService.getParticipantScoreDTOs(page, exercisesOfExam);
        log.info("getParticipantScoresOfExam took {}ms", System.currentTimeMillis() - start);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Double> getAverageScoreOfExam(@PathVariable Long examId, @RequestParam(defaultValue = "true", required = false) boolean onlyConsiderRatedScores) {
        long start = System.currentTimeMillis();
        if (onlyConsiderRatedScores) {
            log.debug("REST request to get average rated scores for exam : {}", examId);
        }
        else {
            log.debug("REST request to get average scores for exam : {}", examId);
        }
        Set<Exercise> includedExercisesOfExam = getIncludedExercisesOfExam(examId);

        Double averageScore = participantScoreService.getAverageScore(onlyConsiderRatedScores, includedExercisesOfExam);
        log.info("getAverageScoreOfExam took {}ms", System.currentTimeMillis() - start);
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
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ParticipantScoreAverageDTO>> getAverageScoreOfParticipantInExam(@PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get average participant scores of participants for exam : {}", examId);
        Set<Exercise> includedExercisesOfExam = getIncludedExercisesOfExam(examId);
        List<ParticipantScoreAverageDTO> resultsOfAllExercises = participantScoreService.getParticipantScoreAverageDTOs(includedExercisesOfExam);
        log.info("getAverageScoreOfParticipantInExam took {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok().body(resultsOfAllExercises);
    }

    private Set<Exercise> getIncludedExercisesOfExam(Long examId) {
        return getExercisesOfExam(examId).stream().filter(exercise -> !exercise.getIncludedInOverallScore().equals(IncludedInOverallScore.NOT_INCLUDED)).collect(toSet());
    }

    private Set<Exercise> getExercisesOfExam(Long examId) {
        Exam exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(examId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, exam.getCourse(), null);
        Set<Exercise> exercisesOfExam = new HashSet<>();
        for (ExerciseGroup exerciseGroup : exam.getExerciseGroups()) {
            exercisesOfExam.addAll(exerciseGroup.getExercises());
        }
        return exercisesOfExam;
    }

}
