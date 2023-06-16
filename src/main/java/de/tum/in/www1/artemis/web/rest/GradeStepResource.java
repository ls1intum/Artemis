package de.tum.in.www1.artemis.web.rest;

import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradeStep;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismVerdict;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.plagiarism.PlagiarismCaseRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.web.rest.dto.GradeDTO;
import de.tum.in.www1.artemis.web.rest.dto.GradeStepsDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing grade steps of a grading scale
 */
@RestController
@RequestMapping("/api")
public class GradeStepResource {

    private final Logger log = LoggerFactory.getLogger(GradeStepResource.class);

    private final AuthorizationCheckService authCheckService;

    private final GradingScaleRepository gradingScaleRepository;

    private final GradeStepRepository gradeStepRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final UserRepository userRepository;

    private final PlagiarismCaseRepository plagiarismCaseRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public GradeStepResource(GradingScaleRepository gradingScaleRepository, GradeStepRepository gradeStepRepository, AuthorizationCheckService authCheckService,
            CourseRepository courseRepository, ExamRepository examRepository, UserRepository userRepository, PlagiarismCaseRepository plagiarismCaseRepository,
            StudentParticipationRepository studentParticipationRepository) {
        this.gradingScaleRepository = gradingScaleRepository;
        this.gradeStepRepository = gradeStepRepository;
        this.authCheckService = authCheckService;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.userRepository = userRepository;
        this.plagiarismCaseRepository = plagiarismCaseRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps : Find all grade steps for the grading scale of a course
     *
     * @param courseId the course to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body a list of grade steps if the grading scale exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/grade-steps")
    @EnforceAtLeastStudent
    public ResponseEntity<GradeStepsDTO> getAllGradeStepsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all grade steps for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        GradingScale gradingScale = gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);
        GradeStepsDTO gradeStepsDTO = prepareGradeStepsDTO(gradingScale, course.getMaxPoints(), course.getTitle());
        return ResponseEntity.ok(gradeStepsDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale/grade-steps : Find all grade steps for the grading scale of an exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the grading scale belongs
     * @return ResponseEntity with status 200 (Ok) with body a list of grade steps if the grading scale exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps")
    @EnforceAtLeastStudent
    public ResponseEntity<GradeStepsDTO> getAllGradeStepsForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all grade steps for exam: {}", examId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = examRepository.findByIdElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        GradingScale gradingScale = gradingScaleRepository.findByExamIdOrElseThrow(examId);
        boolean isInstructor = authCheckService.isAtLeastInstructorInCourse(course, user);
        if (!isInstructor && !exam.resultsPublished()) {
            throw new AccessForbiddenException();
        }
        GradeStepsDTO gradeStepsDTO = prepareGradeStepsDTO(gradingScale, exam.getExamMaxPoints(), exam.getTitle());
        return ResponseEntity.ok(gradeStepsDTO);
    }

    private GradeStepsDTO prepareGradeStepsDTO(GradingScale gradingScale, Integer maxPoints, String title) {
        Set<GradeStep> gradeSteps = gradingScale.getGradeSteps();
        for (GradeStep gradeStep : gradeSteps) {
            gradeStep.setGradingScale(null);
        }
        return new GradeStepsDTO(title, gradingScale.getGradeType(), gradeSteps, maxPoints, gradingScale.getPlagiarismGradeOrDefault(),
                gradingScale.getNoParticipationGradeOrDefault(), gradingScale.getPresentationsNumber(), gradingScale.getPresentationsWeight());
    }

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps/{gradeStepId} : Find a grade step for the grading scale of a course by ID
     *
     * @param courseId    the course to which the grading scale belongs
     * @param gradeStepId the grade step within the grading scale
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/grade-steps/{gradeStepId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradeStep> getGradeStepsByIdForCourse(@PathVariable Long courseId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for course: {}", gradeStepId, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        gradingScaleRepository.findByCourseIdOrElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var gradeStep = gradeStepRepository.findByIdElseThrow(gradeStepId);
        return ResponseEntity.ok(gradeStep);
    }

    /**
     * GET /courses/{courseId}/grading-scale/exams/{examId}/grade-steps/{gradeStepId} : Find a grade step for the grading scale of a course
     *
     * @param courseId    the course to which the exam belongs
     * @param examId      the exam to which the grading scale belongs
     * @param gradeStepId the grade step within the grading scale
     * @return ResponseEntity with status 200 (Ok) with body the grade steps if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/grade-steps/{gradeStepId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<GradeStep> getGradeStepsByIdForExam(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long gradeStepId) {
        log.debug("REST request to get grade step {} for exam: {}", gradeStepId, examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        gradingScaleRepository.findByExamIdOrElseThrow(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var gradeStep = gradeStepRepository.findByIdElseThrow(gradeStepId);
        return ResponseEntity.ok(gradeStep);
    }

    /**
     * GET /courses/{courseId}/grading-scale/grade-steps/match-grade-step : Find a grade step for the grading scale of a course by grade percentage
     *
     * @param courseId        the course to which the grading scale belongs
     * @param gradePercentage the grade percentage the has to be mapped to a grade step
     * @return ResponseEntity with status 200 (Ok) with body the grade if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/grading-scale/match-grade-step")
    @EnforceAtLeastStudent
    public ResponseEntity<GradeDTO> getGradeStepByPercentageForCourse(@PathVariable Long courseId, @RequestParam Double gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for course: {}", gradePercentage, courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<GradingScale> optionalGradingScale = gradingScaleRepository.findByCourseId(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        if (optionalGradingScale.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        GradingScale gradingScale = optionalGradingScale.get();
        GradeStep gradeStep;
        if (!studentParticipationRepository.existsByCourseIdAndStudentId(courseId, user.getId())) {
            gradeStep = new GradeStep();
            gradeStep.setGradeName(gradingScale.getNoParticipationGradeOrDefault());
        }
        else if (plagiarismCaseRepository.findByCourseIdAndStudentId(courseId, user.getId()).stream()
                .anyMatch(plagiarismCase -> PlagiarismVerdict.PLAGIARISM.equals(plagiarismCase.getVerdict()))) {
            gradeStep = new GradeStep();
            gradeStep.setGradeName(gradingScale.getPlagiarismGradeOrDefault());
        }
        else {
            gradeStep = gradingScaleRepository.matchPercentageToGradeStep(gradePercentage, gradingScale.getId());
        }
        GradeDTO gradeDTO = new GradeDTO(gradeStep.getGradeName(), gradeStep.getIsPassingGrade(), gradingScale.getGradeType());
        return ResponseEntity.ok(gradeDTO);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/grading-scale/grade-steps/match-grade-step : Find a grade step for the grading scale of a course by grade percentage
     *
     * @param courseId        the course to which the exam belongs
     * @param examId          the exam to which the grading scale belongs
     * @param gradePercentage the grade percentage the has to be mapped to a grade step
     * @return ResponseEntity with status 200 (Ok) with body the grade if the grading scale and grade step exist and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/grading-scale/match-grade-step")
    @EnforceAtLeastStudent
    public ResponseEntity<GradeDTO> getGradeStepByPercentageForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam Double gradePercentage) {
        log.debug("REST request to get grade step for grade percentage {} for exam: {}", gradePercentage, examId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Course course = courseRepository.findByIdElseThrow(courseId);
        Exam exam = examRepository.findByIdElseThrow(examId);
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        boolean isInstructor = authCheckService.isAtLeastInstructorInCourse(course, user);
        if (gradingScale.isEmpty()) {
            return ResponseEntity.ok(null);
        }
        else if (!isInstructor && !exam.resultsPublished()) {
            throw new AccessForbiddenException();
        }
        GradeStep gradeStep = gradingScaleRepository.matchPercentageToGradeStep(gradePercentage, gradingScale.get().getId());
        GradeDTO gradeDTO = new GradeDTO(gradeStep.getGradeName(), gradeStep.getIsPassingGrade(), gradeStep.getGradingScale().getGradeType());
        return ResponseEntity.ok(gradeDTO);
    }
}
