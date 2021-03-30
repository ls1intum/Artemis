package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.badRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

@RestController
@RequestMapping("/api")
public class GradeStepResource {

    private final Logger log = LoggerFactory.getLogger(GradeStepResource.class);

    private static final String ENTITY_NAME = "gradeStep";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final GradeStepRepository gradeStepRepository;

    private final GradingScaleRepository gradingScaleRepository;

    public GradeStepResource(GradeStepRepository gradeStepRepository, GradingScaleRepository gradingScaleRepository) {
        this.gradeStepRepository = gradeStepRepository;
        this.gradingScaleRepository = gradingScaleRepository;
    }

    @GetMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public List<GradeStep> getAllGradeSteps(@PathVariable Long gradingScaleId) {
        log.debug("REST Request to fetch all grade steps for grading scale: {}", gradingScaleId);
        return gradeStepRepository.findByGradingScale_Id(gradingScaleId);
    }

    @GetMapping("/grading-scale/{gradingScaleId}/grade-step/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepById(@PathVariable Long gradingScaleId, @PathVariable Long gradeStepId) {
        log.debug("REST Request to fetch grade step {} for grading scale: {}", gradeStepId, gradingScaleId);
        Optional<GradeStep> gradeStep = gradeStepRepository.findByIdAndGradingScale_Id(gradeStepId, gradingScaleId);
        return gradeStep.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> createGradeStep(@PathVariable Long gradingScaleId, @RequestBody GradeStep gradeStep) throws URISyntaxException {
        log.debug("REST Request to create grade step for grading scale: {}", gradingScaleId);
        if (gradeStep.getGradingScale() != null) {
            return badRequest();
        }
        GradingScale gradingScale = gradingScaleRepository.findById(gradingScaleId).orElseThrow();
        gradeStep.setGradingScale(gradingScale);
        gradeStep = gradeStepRepository.saveAndFlush(gradeStep);
        return ResponseEntity.created(new URI("/api/grade-step/" + gradeStep.getId())).headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, ""))
                .body(gradeStep);
    }

    @PutMapping("/grading-scale/{gradingScaleId}/grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> updateGradeStep(@PathVariable Long gradingScaleId, @RequestBody GradeStep gradeStep) {
        log.debug("REST Request to update grade step for grading scale: {}", gradingScaleId);
        if (gradeStep.getGradingScale() != null) {
            return badRequest();
        }
        GradingScale gradingScale = gradingScaleRepository.findById(gradingScaleId).orElseThrow();
        gradeStep.setGradingScale(gradingScale);
        gradeStep = gradeStepRepository.saveAndFlush(gradeStep);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(gradeStep);
    }

    @DeleteMapping("/grading-scale/{gradingScaleId}/grade-step/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteGradeStepById(@PathVariable Long gradingScaleId, @PathVariable Long gradeStepId) {
        log.debug("REST Request to delete grade step {} for grading scale: {}", gradeStepId, gradingScaleId);
        gradeStepRepository.deleteById(gradingScaleId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }
}
