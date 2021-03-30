package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradingScale;
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

    public GradingScaleResource(GradingScaleService gradingScaleService, GradingScaleRepository gradingScaleRepository) {
        this.gradingScaleService = gradingScaleService;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    @GetMapping("/courses/{courseId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get grading scale for course: {}", courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        return gradingScale.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> getGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to get grading scale for exam: {}", examId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExam_Id(examId);
        return gradingScale.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/courses/{courseId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForCourse(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) throws URISyntaxException {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        return postRequestGradingScaleResponseEntity("/api/courses/" + courseId + "/grading-scale/", gradingScale, existingGradingScale);
    }

    @PostMapping("/courses/{courseId}/exams/{examId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> createGradingScaleForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody GradingScale gradingScale)
            throws URISyntaxException {
        log.debug("REST request to create a grading scale for exam: {}", examId);
        Optional<GradingScale> existingGradingScale = gradingScaleRepository.findByExam_Id(examId);
        return postRequestGradingScaleResponseEntity("/api/courses/" + courseId + "/exams/" + examId + "/grading-scale/", gradingScale, existingGradingScale);
    }

    @NotNull
    private ResponseEntity<GradingScale> postRequestGradingScaleResponseEntity(String uri, GradingScale gradingScale, Optional<GradingScale> existingGradingScale)
            throws URISyntaxException {
        if (existingGradingScale.isPresent()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "gradingScaleAlreadyExists", "Courses can only have one grading scale.")).body(null);
        }
        if (gradingScale.getGradeSteps() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert(applicationName, true, ENTITY_NAME, "gradingScaleBodyContainsGradeSteps",
                    "Grading scales shouldn't contain grade steps on creation.")).body(null);
        }
        gradingScale = gradingScaleRepository.saveAndFlush(gradingScale);
        return ResponseEntity.created(new URI(uri + gradingScale.getId())).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    @PutMapping("/courses/{courseId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradingScale> updateGradingScale(@PathVariable Long courseId, @RequestBody GradingScale gradingScale) {
        log.debug("REST request to create a grading scale for course: {}", courseId);
        if (gradingScaleRepository.findByCourse_Id(courseId).isEmpty()) {
            return badRequest(ENTITY_NAME, "gradingScaleExists", "Grading scale doesn't exist for the given course.");
        }
        gradingScale = gradingScaleService.updateGradeStepsForGradingScale(gradingScale.getGradeSteps(), gradingScale.getId());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradingScale);
    }

    @DeleteMapping("/courses/{courseId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForCourse(@PathVariable Long courseId) {
        log.debug("REST request to delete the grading scale for course: {}", courseId);
        gradingScaleRepository.deleteGradingScaleForCourse(courseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    @DeleteMapping("/courses/{courseId}/exams/{examId}/grading-scale/")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradingScaleForExam(@PathVariable Long examId) {
        log.debug("REST request to delete the grading scale for exam: {}", examId);
        gradingScaleRepository.deleteGradingScaleForExam(examId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
