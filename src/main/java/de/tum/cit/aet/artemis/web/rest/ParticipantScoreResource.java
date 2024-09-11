package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.exam.Exam;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.ExamRepository;
import de.tum.cit.aet.artemis.security.Role;
import de.tum.cit.aet.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ParticipantScoreService;
import de.tum.cit.aet.artemis.web.rest.dto.score.ScoreDTO;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ParticipantScoreResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipantScoreResource.class);

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
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.cit.aet.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param courseId the id of the course for which to calculate the course scores
     * @return list of scores for every member of the course
     */
    @GetMapping("courses/{courseId}/course-scores")
    @EnforceAtLeastInstructor
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
     * The goal is to switch completely to this much faster server based calculation if the {@link de.tum.cit.aet.artemis.service.listeners.ResultListener}
     * has been battle tested enough.
     *
     * @param examId the id of the exam for which to calculate the exam scores
     * @return list of scores for every registered user in the exam or 404 not found if scores are empty
     */
    @GetMapping("exams/{examId}/exam-scores")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<ScoreDTO>> getScoresOfExam(@PathVariable Long examId) {
        long start = System.currentTimeMillis();
        log.debug("REST request to get exam scores for exam : {}", examId);
        Exam exam = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(examId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, exam.getCourse(), null);
        List<ScoreDTO> scoreDTOS = participantScoreService.calculateExamScores(exam);
        log.info("getScoresOfExam took {}ms", System.currentTimeMillis() - start);
        return scoreDTOS.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok().body(scoreDTOS);
    }
}
