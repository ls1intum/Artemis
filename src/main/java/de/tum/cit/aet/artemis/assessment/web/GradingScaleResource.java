package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;

/**
 * REST controller for managing grading scale
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
public class GradingScaleResource {

    private static final Logger log = LoggerFactory.getLogger(GradingScaleResource.class);

    private static final String ENTITY_NAME = "gradingScale";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final GradingScaleService gradingScaleService;

    private final GradingScaleRepository gradingScaleRepository;

    private final CourseRepository courseRepository;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    public GradingScaleResource(GradingScaleService gradingScaleService, GradingScaleRepository gradingScaleRepository, CourseRepository courseRepository,
            Optional<ExamRepositoryApi> examRepositoryApi, AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.gradingScaleService = gradingScaleService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.courseRepository = courseRepository;
        this.examRepositoryApi = examRepositoryApi;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * GET /courses/{courseId}/grading-scale : Find grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> getGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return gradingScale.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale : Find grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> getGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return gradingScale.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * Search for all grading scales among the grading scales having grade type BONUS. The search will be done by the
     * title of the course or exam that is directly associated with that grading scale. If the user does not have ADMIN role,
     * they can only access the grading scales if they are an instructor in the course related to it. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("grading-scales")
    @EnforceAtLeastInstructor
    public ResponseEntity<SearchResultPageDTO<GradingScale>> getAllGradingScalesInInstructorGroupOnPage(SearchTermPageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(gradingScaleService.getAllOnPageWithSize(search, user));
    }

    /**
     * POST /courses/{courseId}/grading-scale : Create grading scale for course
     *
     * @param courseId     the course to which the grading scale belongs
     * @param gradingScale the grading scale which will be created
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @Valid @RequestBody GradingScale gradingScale) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        validateGradingScale(existingGradingScale, gradingScale);

        validatePresentationsConfiguration(gradingScale);
        updateCourseForGradingScale(gradingScale, course);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/assessment/courses/" + courseId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    private void validateGradingScale(Optional<GradingScale> existingGradingScale, GradingScale gradingScale) {
        if (existingGradingScale.isPresent()) {
            throw new BadRequestAlertException("A grading scale already exists", ENTITY_NAME, "gradingScaleAlreadyExists");
        }
        else if (gradingScale.getGradeSteps() == null || gradingScale.getGradeSteps().isEmpty()) {
            throw new BadRequestAlertException("A grading scale must contain grade steps", ENTITY_NAME, "emptyGradeSteps");
        }
        else if (gradingScale.getId() != null) {
            throw new BadRequestAlertException("A grading scale can't contain a predefined id", ENTITY_NAME, "gradingScaleHasId");
        }
    }

    /**
     * POST /courses/{courseId}/exams/{examId}grading-scale : Create grading scale for exam
     *
     * @param courseId     the course to which the exam belongs
     * @param examId       the exam to which the grading scale belongs
     * @param gradingScale the grading scale which will be created
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @Valid @RequestBody GradingScale gradingScale)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        validateGradingScale(existingGradingScale, gradingScale);
        Exam exam = api.findByIdElseThrow(examId);
        if (gradingScale.getExam().getExamMaxPoints() != exam.getExamMaxPoints()) {
            exam.setExamMaxPoints(gradingScale.getExam().getExamMaxPoints());
            api.save(exam);
        }
        gradingScale.setExam(exam);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/assessment/courses/" + courseId + "/exams/" + examId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/grading-scale : Update grading scale for course
     *
     * @param courseId     the course to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> updateGradingScaleForCourse(@PathVariable Long courseId, @Valid @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale oldGradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScale.setId(oldGradingScale.getId());
        gradingScale.setBonusFrom(oldGradingScale.getBonusFrom()); // bonusFrom should not be affected by this endpoint.

        validatePresentationsConfiguration(gradingScale);
        updateCourseForGradingScale(gradingScale, course);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/grading-scale : Update grading scale for exam
     *
     * @param courseId     the course to which the exam belongs
     * @param examId       the exam to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> updateGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @Valid @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for exam: {}", examId);
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = api.findByIdElseThrow(examId);
        GradingScale oldGradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScale.setId(oldGradingScale.getId());
        gradingScale.setBonusFrom(oldGradingScale.getBonusFrom()); // bonusFrom should not be affected by this endpoint.
        if (gradingScale.getExam().getExamMaxPoints() != exam.getExamMaxPoints()) {
            exam.setExamMaxPoints(gradingScale.getExam().getExamMaxPoints());
            api.save(exam);
        }
        gradingScale.setExam(exam);
        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * DELETE /courses/{courseId}/grading-scale : Delete grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) if the grading scale is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to delete the grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScaleRepository.delete(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/grading-scale : Delete grading scale for course
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) if the grading scale is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to delete the grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScaleRepository.delete(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    private void updateCourseForGradingScale(GradingScale gradingScale, Course course) {
        if (gradingScale == null) {
            return;
        }

        if (course != null && gradingScale.getCourse() != null && (!Objects.equals(gradingScale.getCourse().getMaxPoints(), course.getMaxPoints())
                || !Objects.equals(gradingScale.getCourse().getPresentationScore(), course.getPresentationScore()))) {
            course.setMaxPoints(gradingScale.getCourse().getMaxPoints());
            course.setPresentationScore(gradingScale.getCourse().getPresentationScore());
            courseRepository.save(course);
        }
        gradingScale.setCourse(course);
    }

    private void validatePresentationsConfiguration(GradingScale gradingScale) {
        if (gradingScale == null) {
            return;
        }

        final var course = getCourse(gradingScale);

        // Check validity of graded presentation configuration
        if (gradingScale.getPresentationsNumber() != null || gradingScale.getPresentationsWeight() != null) {
            // The gradingScale must belong to a course.
            if (course == null) {
                throw new BadRequestAlertException("You cannot set up graded presentations if the gradingScale does not belong to a course", ENTITY_NAME,
                        "invalidCourseForGradedPresentationsConfiguration");
            }
            // The presentationsNumber must be above 0. The presentationsWeight must be between 0 and 99.
            if (gradingScale.getPresentationsNumber() == null || gradingScale.getPresentationsNumber() < 1 || gradingScale.getPresentationsWeight() == null
                    || gradingScale.getPresentationsWeight() < 0 || gradingScale.getPresentationsWeight() > 99) {
                throw new BadRequestAlertException(
                        "The number of presentations must be a whole number above 0 and the combined weight of all presentations must be between 0 and 99!", ENTITY_NAME,
                        "invalidGradedPresentationsConfiguration");
            }
        }
    }

    private static Course getCourse(GradingScale gradingScale) {
        Course course = gradingScale.getCourse();

        // Check validity of basic presentation configuration
        if (course != null && course.getPresentationScore() != null && course.getPresentationScore() != 0) {
            // The presentationsNumber and presentationsWeight must be null.
            if (gradingScale.getPresentationsNumber() != null || gradingScale.getPresentationsWeight() != null) {
                throw new BadRequestAlertException("You cannot set up graded presentations if the course is already set up for basic presentations", ENTITY_NAME,
                        "basicPresentationAlreadySet");
            }
            // The presentationScore must be above 0.
            if (course.getPresentationScore() <= 0) {
                throw new BadRequestAlertException("The number of presentations must be a whole number above 0!", ENTITY_NAME, "invalidBasicPresentationsConfiguration");
            }
        }
        return course;
    }
}
