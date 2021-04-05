package de.tum.in.www1.artemis.web.rest;

import java.util.List;
import java.util.Optional;

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

/**
 * REST controller for managing grade steps of a grading scale
 */
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

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps : Find all grade steps for the grading scale of a course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body a list of grade steps if the grading scale exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<GradeStep>> getAllGradeStepsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all grade steps for course: {}", courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        List<GradeStep> gradeSteps = gradeStepRepository.findByGradingScale_Id(gradingScale.getId());
        return ResponseEntity.ok(gradeSteps);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale/grade-steps : Find all grade steps for the grading scale of an exam
     *
     * @param examId the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body a list of grade steps if the grading scale exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<List<GradeStep>> getAllGradeStepsForExam(@PathVariable Long examId) {
        log.debug("REST request to get all grade steps for exam: {}", examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        List<GradeStep> gradeSteps = gradeStepRepository.findByGradingScale_Id(gradingScale.getId());
        return ResponseEntity.ok(gradeSteps);
    }

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps/{gradeStepId} : Find a grade step for the grading scale of a course by ID
     *
     * @param courseId the course to which the grading scale belongs
     * @param gradeStepId the grade step within the grading scale
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/grade-steps/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepsByIdForCourse(@PathVariable Long courseId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for course: {}", gradeStepId, courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        Optional<GradeStep> gradeStep = gradeStepRepository.findByIdAndGradingScale_Id(gradeStepId, gradingScale.getId());
        return gradeStep.map(ResponseEntity::ok).orElseGet(ResponseUtil::notFound);
    }

    /**
     * GET /courses/{courseId}/grading-scale/exams/{examId}/grade-steps/{gradeStepId} : Find a grade step for the grading scale of a course
     *
     * @param examId the exam to which the grading scale belongs
     * @param gradeStepId the grade step within the grading scale
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps/{gradeStepId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepsByIdForExam(@PathVariable Long examId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for exam: {}", gradeStepId, examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        Optional<GradeStep> gradeStep = gradeStepRepository.findByIdAndGradingScale_Id(gradeStepId, gradingScale.getId());
        return gradeStep.map(ResponseEntity::ok).orElseGet(ResponseUtil::notFound);
    }

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps/match-grade-step : Find a grade step for the grading scale of a course by grade percentage
     *
     * @param courseId the course to which the grading scale belongs
     * @param gradePercentage the grade percentage the has to be mapped to a grade step
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/match-grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepByPercentageForCourse(@PathVariable Long courseId, @RequestParam Double gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for course: {}", gradePercentage, courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        GradeStep gradeStep = gradingScaleService.matchPercentageToGradeStep(gradePercentage, gradingScale.getId());
        return ResponseEntity.ok(gradeStep);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale/grade-steps/match-grade-step : Find a grade step for the grading scale of a course by grade percentage
     *
     * @param examId the exam to which the grading scale belongs
     * @param gradePercentage the grade percentage the has to be mapped to a grade step
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/match-grade-step")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<GradeStep> getGradeStepByPercentageForExam(@PathVariable Long examId, @RequestParam Double gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for exam: {}", gradePercentage, examId);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        GradeStep gradeStep = gradingScaleService.matchPercentageToGradeStep(gradePercentage, gradingScale.getId());
        return ResponseEntity.ok(gradeStep);
    }
}
