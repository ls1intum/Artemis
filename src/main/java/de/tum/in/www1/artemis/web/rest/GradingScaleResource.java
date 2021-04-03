package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

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

    @GetMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get grading scale for course: {}", courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        return ResponseEntity.ok(gradingScale);
    }

    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to get grading scale for exam: {}", examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        return ResponseEntity.ok(gradingScale);
    }

    @PostMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        if (gradingScale.getGradeSteps() == null) {
            return badRequest();
        }
        Course course = courseRepository.findById(courseId).orElseThrow();
        gradingScale.setCourse(course);

        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/grading-scale/")).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(gradingScale);
    }

    @PostMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody GradingScale gradingScale)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        gradingScaleRepository.findByExamIdOrElseThrow(examId);
        if (gradingScale.getGradeSteps() == null) {
            return badRequest();
        }
        Exam exam = examRepository.findById(examId).orElseThrow();
        gradingScale.setExam(exam);

        gradingScale = gradingScaleService.saveGradingScale(gradingScale);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/grading-scale/"))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    @PutMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> updateGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for course: {}", courseId);
        GradingScale oldGradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        gradingScale.setId(oldGradingScale.getId());
        gradingScale.setCourse(oldGradingScale.getCourse());
        gradingScale = gradingScaleService.updateGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    @PutMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> updateGradingScaleForExam(@PathVariable Long examId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to update a grading scale for exam: {}", examId);
        if (gradingScaleRepository.findByExam_Id(examId).isEmpty()) {
            return badRequest(ENTITY_NAME, "gradingScaleExists", "Grading scale doesn't exist for the given course.");
        }
        gradingScale = gradingScaleService.updateGradingScale(gradingScale);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    @DeleteMapping("/courses/{courseId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to delete the grading scale for course: {}", courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        gradingScaleService.deleteAllGradeStepsForGradingScale(gradingScale);
        gradingScaleRepository.deleteInBatch(List.of(gradingScale));
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    @DeleteMapping("/courses/{courseId}/exams/{examId}/grading-scale")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to delete the grading scale for exam: {}", examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        gradingScaleService.deleteAllGradeStepsForGradingScale(gradingScale);
        gradingScaleRepository.deleteInBatch(List.of(gradingScale));
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
