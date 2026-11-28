package de.tum.cit.aet.artemis.atlas.web;

import java.util.List;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionReviewDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSessionSummaryDTO;
import de.tum.cit.aet.artemis.atlas.dto.practice.PracticeSubmissionDTO;
import de.tum.cit.aet.artemis.atlas.service.practice.PracticeQuizService;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * REST controller for managing on-demand adaptive practice quiz sessions in Atlas.
 */
@RestController
@Conditional(AtlasEnabled.class)
@FeatureToggle(Feature.SmartQuizzes)
@FeatureUsage(UserFeature.QUIZ_PRACTICE)
@Lazy
@RequestMapping("api/atlas/")
public class PracticeQuizResource {

    private static final Logger log = LoggerFactory.getLogger(PracticeQuizResource.class);

    private final PracticeQuizService practiceQuizService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    public PracticeQuizResource(PracticeQuizService practiceQuizService, CourseRepository courseRepository, UserRepository userRepository) {
        this.practiceQuizService = practiceQuizService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
    }

    /**
     * POST courses/{courseId}/smart-quizzes/start : Starts a new on-demand practice quiz session
     * with a 50/50 split of adaptive and non-adaptive items.
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK) and the session DTO
     */
    @PostMapping("courses/{courseId}/smart-quizzes/start")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PracticeSessionDTO> startPracticeQuiz(@PathVariable long courseId) {
        log.debug("REST request to start adaptive practice quiz in course: {}", courseId);
        User user = userRepository.getUser();
        Course course = courseRepository.findByIdElseThrow(courseId);
        PracticeSessionDTO sessionDTO = practiceQuizService.startPracticeSession(user, course);
        return ResponseEntity.ok(sessionDTO);
    }

    /**
     * POST courses/{courseId}/smart-quizzes/sessions/{sessionId}/submit : Submits responses for a practice quiz session.
     *
     * @param courseId      the ID of the course
     * @param sessionId     the ID of the practice session
     * @param submissionDTO the submitted answers
     * @return the ResponseEntity with status 200 (OK) and the review DTO
     */
    @PostMapping("courses/{courseId}/smart-quizzes/sessions/{sessionId}/submit")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PracticeSessionReviewDTO> submitPracticeQuiz(@PathVariable long courseId, @PathVariable long sessionId,
            @Valid @RequestBody PracticeSubmissionDTO submissionDTO) {
        log.debug("REST request to submit practice quiz session {} in course: {}", sessionId, courseId);
        User user = userRepository.getUser();
        PracticeSessionReviewDTO reviewDTO = practiceQuizService.submitPracticeSession(courseId, sessionId, user, submissionDTO);
        return ResponseEntity.ok(reviewDTO);
    }

    /**
     * GET courses/{courseId}/smart-quizzes/sessions : Gets all past practice sessions for the current student in this course.
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK) and the list of session summaries
     */
    @GetMapping("courses/{courseId}/smart-quizzes/sessions")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<PracticeSessionSummaryDTO>> getPracticeSessions(@PathVariable long courseId) {
        log.debug("REST request to get past practice quiz sessions for course: {}", courseId);
        User user = userRepository.getUser();
        List<PracticeSessionSummaryDTO> summaries = practiceQuizService.getPracticeSessionsForStudent(courseId, user);
        return ResponseEntity.ok(summaries);
    }

    /**
     * GET courses/{courseId}/smart-quizzes/sessions/{sessionId} : Gets a completed practice session for review.
     *
     * @param courseId  the ID of the course
     * @param sessionId the ID of the practice session
     * @return the ResponseEntity with status 200 (OK) and the review DTO
     */
    @GetMapping("courses/{courseId}/smart-quizzes/sessions/{sessionId}")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PracticeSessionReviewDTO> getPracticeSessionForReview(@PathVariable long courseId, @PathVariable long sessionId) {
        log.debug("REST request to review practice quiz session {} in course: {}", sessionId, courseId);
        User user = userRepository.getUser();
        PracticeSessionReviewDTO reviewDTO = practiceQuizService.getPracticeSessionForReview(courseId, sessionId, user);
        return ResponseEntity.ok(reviewDTO);
    }

    /**
     * GET courses/{courseId}/smart-quizzes/sessions/{sessionId}/resume : Gets an unfinished practice session of the current student to resume it.
     *
     * @param courseId  the ID of the course
     * @param sessionId the ID of the practice session
     * @return the ResponseEntity with status 200 (OK) and the session DTO without solutions
     */
    @GetMapping("courses/{courseId}/smart-quizzes/sessions/{sessionId}/resume")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PracticeSessionDTO> resumePracticeSession(@PathVariable long courseId, @PathVariable long sessionId) {
        log.debug("REST request to resume practice quiz session {} in course: {}", sessionId, courseId);
        User user = userRepository.getUser();
        return ResponseEntity.ok(practiceQuizService.getUnfinishedSession(courseId, sessionId, user));
    }

    /**
     * GET courses/{courseId}/smart-quizzes/current : Gets the current unfinished practice session if one exists.
     *
     * @param courseId the ID of the course
     * @return the ResponseEntity with status 200 (OK) and the session DTO or 204 (NO_CONTENT)
     */
    @GetMapping("courses/{courseId}/smart-quizzes/current")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<PracticeSessionDTO> getLatestUnfinishedSession(@PathVariable long courseId) {
        log.debug("REST request to get unfinished practice session for course: {}", courseId);
        User user = userRepository.getUser();
        return practiceQuizService.getLatestUnfinishedSession(courseId, user).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
