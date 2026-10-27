package de.tum.cit.aet.artemis.hyperion.web;

import java.util.List;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.api.CourseCompetencyApi;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolBuildStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolReviewDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionPoolService;

/**
 * REST controller for a course's Hyperion quiz question pool and its settings.
 */
@Conditional(HyperionEnabled.class)
@Lazy
@FeatureUsage(UserFeature.HYPERION_QUIZ_GENERATION)
@RestController
@RequestMapping("api/hyperion/")
public class HyperionQuizQuestionPoolResource {

    private static final Logger log = LoggerFactory.getLogger(HyperionQuizQuestionPoolResource.class);

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final HyperionQuizQuestionPoolService poolService;

    private final Optional<CourseCompetencyApi> courseCompetencyApi;

    public HyperionQuizQuestionPoolResource(CourseRepository courseRepository, UserRepository userRepository, HyperionQuizQuestionPoolService poolService,
            Optional<CourseCompetencyApi> courseCompetencyApi) {
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.poolService = poolService;
        this.courseCompetencyApi = courseCompetencyApi;
    }

    /**
     * POST /courses/{courseId}/quiz-question-pool/build : Top the course's quiz question pool up to its target and build it in the background.
     * Requires the Atlas module and at least one competency in the course.
     *
     * @param courseId the id of the course
     * @return 202 with the number of items added when a build was started, 409 when a build of this course is already running
     */
    @EnforceAtLeastInstructorInCourse
    @PostMapping("courses/{courseId}/quiz-question-pool/build")
    public ResponseEntity<QuizQuestionPoolBuildStartDTO> startQuizQuestionPoolBuild(@PathVariable long courseId) {
        log.debug("REST request to build the Hyperion quiz question pool of course [{}]", courseId);
        requireCompetencies(courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Long userId = SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findIdByLogin).orElse(null);
        QuizQuestionPoolBuildStartDTO result = poolService.startBuild(course, userId);
        return ResponseEntity.status(result.started() ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT).body(result);
    }

    /**
     * GET /courses/{courseId}/quiz-question-pool/progress : How far the course's pool has come.
     *
     * @param courseId the id of the course
     * @return the progress
     */
    @EnforceAtLeastEditorInCourse
    @GetMapping("courses/{courseId}/quiz-question-pool/progress")
    public ResponseEntity<QuizQuestionPoolProgressDTO> getQuizQuestionPoolProgress(@PathVariable long courseId) {
        log.debug("REST request for the Hyperion quiz question pool progress of course [{}]", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(poolService.getProgress(courseId));
    }

    /**
     * GET /courses/{courseId}/quiz-question-pool/questions : The items of the course's pool with their questions and decisions.
     *
     * @param courseId     the id of the course
     * @param competencyId restricts the result to one competency when given
     * @return the items in cell order
     */
    @EnforceAtLeastEditorInCourse
    @GetMapping("courses/{courseId}/quiz-question-pool/questions")
    public ResponseEntity<List<QuizQuestionPoolItemDTO>> getQuizQuestionPoolQuestions(@PathVariable long courseId, @RequestParam(required = false) Long competencyId) {
        log.debug("REST request for the Hyperion quiz question pool of course [{}]", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(poolService.getItems(courseId, competencyId));
    }

    /**
     * GET /courses/{courseId}/quiz-question-pool/settings : The course's Hyperion settings.
     *
     * @param courseId the id of the course
     * @return the settings, defaults when none were saved yet
     */
    @EnforceAtLeastInstructorInCourse
    @GetMapping("courses/{courseId}/quiz-question-pool/settings")
    public ResponseEntity<HyperionCourseSettingsDTO> getQuizQuestionPoolSettings(@PathVariable long courseId) {
        log.debug("REST request for the Hyperion settings of course [{}]", courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(poolService.getSettings(courseId));
    }

    /**
     * PUT /courses/{courseId}/quiz-question-pool/settings : Replace the course's Hyperion settings.
     *
     * @param courseId the id of the course
     * @param settings the settings to store
     * @return the stored settings
     */
    @EnforceAtLeastInstructorInCourse
    @PutMapping("courses/{courseId}/quiz-question-pool/settings")
    public ResponseEntity<HyperionCourseSettingsDTO> updateQuizQuestionPoolSettings(@PathVariable long courseId, @Valid @RequestBody HyperionCourseSettingsDTO settings) {
        log.debug("REST request to update the Hyperion settings of course [{}]", courseId);
        if (!settings.hasCompleteDifficultyMix()) {
            throw new BadRequestAlertException("The difficulty shares must add up to 100 percent", "QuizQuestionPool", "difficultyMix");
        }
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(poolService.updateSettings(courseId, settings));
    }

    /**
     * PUT /courses/{courseId}/quiz-question-pool/questions/{questionId}/review : Record the review decision on one pool question.
     *
     * @param courseId   the id of the course
     * @param questionId the id of the pool item
     * @param review     the decision
     * @return the updated item
     */
    @EnforceAtLeastEditorInCourse
    @PutMapping("courses/{courseId}/quiz-question-pool/questions/{questionId}/review")
    public ResponseEntity<QuizQuestionPoolItemDTO> updateQuizQuestionPoolReview(@PathVariable long courseId, @PathVariable long questionId,
            @Valid @RequestBody QuizQuestionPoolReviewDTO review) {
        log.debug("REST request to review quiz question pool item [{}] of course [{}]", questionId, courseId);
        courseRepository.findByIdElseThrow(courseId);
        return ResponseEntity.ok(poolService.updateReviewState(courseId, questionId, review.reviewState()));
    }

    private void requireCompetencies(long courseId) {
        if (courseCompetencyApi.isEmpty()) {
            throw new BadRequestAlertException("The quiz question pool requires the Atlas module to be enabled", "QuizQuestionPool", "atlasNotEnabled");
        }
        if (!courseCompetencyApi.get().courseHasCompetencies(courseId)) {
            throw new BadRequestAlertException("This course has no competencies configured", "QuizQuestionPool", "noCompetencies");
        }
    }
}
