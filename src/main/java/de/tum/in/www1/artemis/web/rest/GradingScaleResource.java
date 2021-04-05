package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;
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

    public GradingScaleResource(GradingScaleService gradingScaleService, GradingScaleRepository gradingScaleRepository, CourseRepository courseRepository,
            ExamRepository examRepository) {
        this.gradingScaleService = gradingScaleService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
    }

    /**
     * GET /courses/{courseId}/grading-scale : Find grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get grading scale for course: {}", courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        return ResponseEntity.ok(gradingScale);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale : Find grading scale for exam
     *
     * @param examId the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body the grading scale if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to get grading scale for exam: {}", examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        return ResponseEntity.ok(gradingScale);
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
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        if (existingGradingScale.isPresent()) {
            return badRequest(ENTITY_NAME, "gradingScaleAlreadyExists", "A grading scale already exists for the selected course");
        }
        else if (gradingScale.getGradeSteps() == null) {
            return badRequest(ENTITY_NAME, "noGradeSteps", "A grading scale must contain grade steps");
        }
        Course course = courseRepository.findById(courseId).orElseThrow();
        gradingScale.setCourse(course);

        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/grading-scale/")).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(gradingScale);
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
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody GradingScale gradingScale)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByExam_Id(examId);
        if (existingGradingScale.isPresent()) {
            return badRequest(ENTITY_NAME, "gradingScaleAlreadyExists", "A grading scale already exists for the selected exam");
        }
        else if (gradingScale.getGradeSteps() == null) {
            return badRequest(ENTITY_NAME, "noGradeSteps", "A grading scale must contain grade steps");
        }
        Exam exam = examRepository.findById(examId).orElseThrow();
        gradingScale.setExam(exam);

        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    /**
     * PUT /courses/{courseId}/grading-scale : Update grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> updateGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for course: {}", courseId);
        GradingScale oldGradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        gradingScale.setId(oldGradingScale.getId());
        gradingScale.setCourse(oldGradingScale.getCourse());
        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/grading-scale : Update grading scale for exam
     *
     * @param examId the exam to which the grading scale belongs
     * @param gradingScale the grading scale which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated grading scale if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> updateGradingScaleForExam(@PathVariable Long examId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for exam: {}", examId);
        GradingScale oldGradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        gradingScale.setId(oldGradingScale.getId());
        gradingScale.setExam(oldGradingScale.getExam());
        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    /**
     * DELETE /courses/{courseId}/grading-scale : Delete grading scale for course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) if the grading scale is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to delete the grading scale for course: {}", courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        gradingScaleRepository.deleteById(gradingScale.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/grading-scale : Delete grading scale for course
     *
     * @param examId the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) if the grading scale is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to delete the grading scale for exam: {}", examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        gradingScaleRepository.deleteById(gradingScale.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
