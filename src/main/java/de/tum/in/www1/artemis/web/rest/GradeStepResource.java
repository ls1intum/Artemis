package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.notFound;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.GradeStepRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.service.GradingScaleService;
import de.tum.in.www1.artemis.web.rest.util.ResponseUtil;

@RestController
@RequestMapping("/api")
public class GradeStepResource {

    private final Logger log = LoggerFactory.getLogger(GradingScaleResource.class);

    private final GradingScaleService gradingScaleService;

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    public GradeStepResource(GradingScaleService gradingScaleService, GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository) {
        this.gradingScaleService = gradingScaleService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradeStepRepository = gradeStepRepository;
    }

    @GetMapping("/courses/{courseId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<GradeStep>> getAllGradeStepsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all grade steps for course: {}", courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        return gradingScale.map(scale -> ResponseEntity.ok(scale.getGradeSteps())).orElseGet(ResponseUtil::notFound);
    }

    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<GradeStep>> getAllGradeStepsForExam(@PathVariable Long examId) {
        log.debug("REST request to get all grade steps for exam: {}", examId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExam_Id(examId);
        return gradingScale.map(scale -> ResponseEntity.ok(scale.getGradeSteps())).orElseGet(ResponseUtil::notFound);
    }

    @GetMapping("/courses/{courseId}/grading-scale/grade-steps/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepsByIdForCourse(@PathVariable Long courseId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for course: {}", gradeStepId, courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        return handleGradeStepGetRequest(gradingScale, gradeStepId);
    }

    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepsByIdForExam(@PathVariable Long examId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for exam: {}", gradeStepId, examId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExam_Id(examId);
        return handleGradeStepGetRequest(gradingScale, gradeStepId);
    }

    private ResponseEntity<GradeStep> handleGradeStepGetRequest(Optional<GradingScale> gradingScale, Long gradeStepId) {
        if (gradingScale.isEmpty()) {
            return notFound();
        }
        Optional<GradeStep> gradeStep = gradeStepRepository.findByIdAndGradingScale_Id(gradeStepId, gradingScale.get().getId());
        return gradeStep.map(ResponseEntity::ok).orElseGet(ResponseUtil::notFound);
    }

    @GetMapping("/courses/{courseId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepByPercentageForCourse(@PathVariable Long courseId, @RequestParam Integer gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for course: {}", gradePercentage, courseId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourse_Id(courseId);
        if (gradingScale.isEmpty()) {
            return notFound();
        }
        GradeStep gradeStep = gradingScaleService.matchPercentageToGradeStep(gradePercentage, gradingScale.get().getId());
        return ResponseEntity.ok(gradeStep);
    }

    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepByPercentageForExam(@PathVariable Long examId, @RequestParam Integer gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for exam: {}", gradePercentage, examId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExam_Id(examId);
        if (gradingScale.isEmpty()) {
            return notFound();
        }
        GradeStep gradeStep = gradingScaleService.matchPercentageToGradeStep(gradePercentage, gradingScale.get().getId());
        return ResponseEntity.ok(gradeStep);
    }
}
