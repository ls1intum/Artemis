package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
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
import de.tum.cit.aet.artemis.assessment.dto.GradingScaleUpdateDTO;
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
     * @param courseId the course to which the grading scale belongs
     * @param dto      the DTO containing the grading scale values
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @Valid @RequestBody GradingScaleUpdateDTO dto) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        validateGradingScaleForCreate(existingGradingScale, dto);

        // Create grading scale from DTO
        GradingScale gradingScale = dto.toEntity();
        gradingScale.setCourse(course);

        // Apply DTO values to course before validation so that presentation config is validated correctly
        applyCourseValuesFromDTO(dto, course);
        validatePresentationsConfiguration(gradingScale);
        saveCourseIfChanged(dto, course);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/assessment/courses/" + courseId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    private void validateGradingScaleForCreate(Optional<GradingScale> existingGradingScale, GradingScaleUpdateDTO dto) {
        if (existingGradingScale.isPresent()) {
            throw new BadRequestAlertException("A grading scale already exists", ENTITY_NAME, "gradingScaleAlreadyExists");
        }
        else if (dto.gradeSteps() == null || dto.gradeSteps().isEmpty()) {
            throw new BadRequestAlertException("A grading scale must contain grade steps", ENTITY_NAME, "emptyGradeSteps");
        }
    }

    /**
     * POST /courses/{courseId}/exams/{examId}grading-scale : Create grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the grading scale belongs
     * @param dto      the DTO containing the grading scale values
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @Valid @RequestBody GradingScaleUpdateDTO dto)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        validateGradingScaleForCreate(existingGradingScale, dto);

        Exam exam = api.findByIdElseThrow(examId);
        if (dto.examMaxPoints() != null && !dto.examMaxPoints().equals(exam.getExamMaxPoints())) {
            exam.setExamMaxPoints(dto.examMaxPoints());
            api.save(exam);
        }

        // Create grading scale from DTO
        GradingScale gradingScale = dto.toEntity();
        gradingScale.setExam(exam);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/assessment/courses/" + courseId + "/exams/" + examId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/grading-scale : Update grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @param dto      the DTO containing the grading scale update values
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("courses/{courseId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> updateGradingScaleForCourse(@PathVariable Long courseId, @Valid @RequestBody GradingScaleUpdateDTO dto) {
        log.debug("REST request to update a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        // Fetch the existing grading scale from the database (this is the managed entity)
        // Note: gradeSteps are eagerly fetched due to FetchType.EAGER
        GradingScale existingGradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);

        // Apply DTO values to the managed entity
        dto.applyTo(existingGradingScale);
        existingGradingScale.setCourse(course);

        // Apply DTO values to course before validation so that presentation config is validated correctly
        applyCourseValuesFromDTO(dto, course);
        validatePresentationsConfiguration(existingGradingScale);
        saveCourseIfChanged(dto, course);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(existingGradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/grading-scale : Update grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the grading scale belongs
     * @param dto      the DTO containing the grading scale update values
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("courses/{courseId}/exams/{examId}/grading-scale")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradingScale> updateGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @Valid @RequestBody GradingScaleUpdateDTO dto) {
        log.debug("REST request to update a grading scale for exam: {}", examId);
        ExamRepositoryApi api = examRepositoryApi.orElseThrow(() -> new ExamApiNotPresentException(ExamRepositoryApi.class));

        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = api.findByIdElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        // Fetch the existing grading scale from the database (this is the managed entity)
        // Note: gradeSteps are eagerly fetched due to FetchType.EAGER
        GradingScale existingGradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);

        // Update exam max points if provided
        if (dto.examMaxPoints() != null && dto.examMaxPoints() != exam.getExamMaxPoints()) {
            exam.setExamMaxPoints(dto.examMaxPoints());
            api.save(exam);
        }

        // Apply DTO values to the managed entity
        dto.applyTo(existingGradingScale);
        existingGradingScale.setExam(exam);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(existingGradingScale);
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

    /**
     * Applies course properties from DTO to the course object (in memory, does not save).
     * This should be called before validation so that the validation uses the intended values.
     */
    private void applyCourseValuesFromDTO(GradingScaleUpdateDTO dto, Course course) {
        if (dto == null || course == null) {
            return;
        }

        if (dto.courseMaxPoints() != null) {
            course.setMaxPoints(dto.courseMaxPoints());
        }
        if (dto.coursePresentationScore() != null) {
            course.setPresentationScore(dto.coursePresentationScore());
        }
    }

    /**
     * Saves the course if it was modified by DTO values.
     * Should be called after validation succeeds.
     */
    private void saveCourseIfChanged(GradingScaleUpdateDTO dto, Course course) {
        if (dto == null || course == null) {
            return;
        }

        // If either value was provided in the DTO, we need to save
        if (dto.courseMaxPoints() != null || dto.coursePresentationScore() != null) {
            courseRepository.save(course);
        }
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
