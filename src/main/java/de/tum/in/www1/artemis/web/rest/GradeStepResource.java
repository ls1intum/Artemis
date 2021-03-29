package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.service.GradeStepService;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class GradeStepResource {

    private final Logger log = LoggerFactory.getLogger(GradeStepResource.class);

    private static final String ENTITY_NAME = "gradeStep";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private GradeStepService gradeStepService;

    public GradeStepResource(GradeStepService gradeStepService) {
        this.gradeStepService = gradeStepService;
    }

    @GetMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public List<GradeStep> getAllGradeSteps(@PathVariable Long gradingScaleId) {
        return gradeStepService.findAllGradeStepsForGradingScaleById(gradingScaleId);
    }

    @GetMapping("/grading-scale/{gradingScaleId}/grade-step/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepById(@PathVariable Long gradingScaleId, @PathVariable Long gradeStepId) {
        GradeStep gradeStep = gradeStepService.findGradeStepByIdForGradingScaleByGradingScaleId(gradeStepId, gradingScaleId);
        return ResponseEntity.ok(gradeStep);
    }

    @PostMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> createGradeStep(@PathVariable Long gradingScaleId, @RequestBody GradeStep gradeStep) throws URISyntaxException {
        gradeStep = gradeStepService.saveGradeStepForGradingScaleById(gradeStep, gradingScaleId);
        return ResponseEntity.created(new URI("/api/grade-step/" + gradeStep.getId())).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(gradeStep);
    }

    @PutMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> updateGradeStep(@PathVariable Long gradingScaleId, @RequestBody GradeStep gradeStep) {
        gradeStep = gradeStepService.saveGradeStepForGradingScaleById(gradeStep, gradingScaleId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradeStep);
    }

    @DeleteMapping("/grading-scale/{gradingScaleId}/grade-step/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradeStepById(@PathVariable Long gradingScaleId, @PathVariable Long gradeStepId) {
        gradeStepService.deleteGradeStepById(gradeStepId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }
}
