package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.GradingScaleService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing grading scale
 */
@RestController
@RequestMapping("/api")
public class GradingScaleResource {

    private final Logger log = LoggerFactory.getLogger(GradingScaleResource.class);

    private static final String ENTITY_NAME = "gradingScale";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final GradingScaleService gradingScaleService;

    private final GradingScaleRepository gradingScaleRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final AuthorizationCheckService authCheckService;

    public GradingScaleResource(GradingScaleService gradingScaleService, GradingScaleRepository gradingScaleRepository, CourseRepository courseRepository,
            ExamRepository examRepository, AuthorizationCheckService authCheckService) {
        this.gradingScaleService = gradingScaleService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * GET /courses/{courseId}/grading-scale : Find grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
     * @param examId the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<GradingScale> getGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return gradingScale.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * POST /courses/{courseId}/grading-scale : Create grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @param gradingScale the grading scale which will be created
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (existingGradingScale.isPresent()) {
            return badRequest(ENTITY_NAME, "gradingScaleAlreadyExists", "A grading scale already exists for the selected course");
        }
        else if (gradingScale.getGradeSteps() == null || gradingScale.getGradeSteps().isEmpty()) {
            return badRequest(ENTITY_NAME, "emptyGradeSteps", "A grading scale must contain grade steps");
        }
        else if (gradingScale.getId() != null) {
            return badRequest(ENTITY_NAME, "gradingScaleHasId", "A grading scale can't contain a predefined id");
        }

        if (!Objects.equals(gradingScale.getCourse().getMaxPoints(), course.getMaxPoints())) {
            course.setMaxPoints(gradingScale.getCourse().getMaxPoints());
            courseRepository.save(course);
        }
        gradingScale.setCourse(course);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/grading-scale/")).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(savedGradingScale);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}grading-scale : Create grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the grading scale belongs
     * @param gradingScale the grading scale which will be created
     * @return ResponseEntity with status 201 (Created) with body the new grading scale if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody GradingScale gradingScale)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (existingGradingScale.isPresent()) {
            return badRequest(ENTITY_NAME, "gradingScaleAlreadyExists", "A grading scale already exists for the selected exam");
        }
        else if (gradingScale.getGradeSteps() == null || gradingScale.getGradeSteps().isEmpty()) {
            return badRequest(ENTITY_NAME, "emptyGradeSteps", "A grading scale must contain grade steps");
        }
        else if (gradingScale.getId() != null) {
            return badRequest(ENTITY_NAME, "gradingScaleHasId", "A grading scale can't contain a predefined id");
        }
        Exam exam = examRepository.findByIdElseThrow(examId);
        if (gradingScale.getExam().getMaxPoints() != exam.getMaxPoints()) {
            exam.setMaxPoints(gradingScale.getExam().getMaxPoints());
            examRepository.save(exam);
        }
        gradingScale.setExam(exam);

        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/grading-scale : Update grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<GradingScale> updateGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale oldGradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScale.setId(oldGradingScale.getId());
        if (!Objects.equals(gradingScale.getCourse().getMaxPoints(), course.getMaxPoints())) {
            course.setMaxPoints(gradingScale.getCourse().getMaxPoints());
            courseRepository.save(course);
        }
        gradingScale.setCourse(course);
        GradingScale savedGradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedGradingScale);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/grading-scale : Update grading scale for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<GradingScale> updateGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = examRepository.findByIdElseThrow(examId);
        GradingScale oldGradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScale.setId(oldGradingScale.getId());
        if (gradingScale.getExam().getMaxPoints() != exam.getMaxPoints()) {
            exam.setMaxPoints(gradingScale.getExam().getMaxPoints());
            examRepository.save(exam);
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
    @DeleteMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
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
     * @param examId the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) if the grading scale is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to delete the grading scale for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        gradingScaleRepository.delete(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
