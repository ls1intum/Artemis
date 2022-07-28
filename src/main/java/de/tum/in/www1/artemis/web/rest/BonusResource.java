package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Bonus;
import de.tum.in.www1.artemis.domain.BonusStrategy;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.GradingScale;
import de.tum.in.www1.artemis.repository.BonusRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExamRepository;
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.BonusService;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing bonus source
 */
@RestController
@RequestMapping("/api")
public class BonusResource {

    private final Logger log = LoggerFactory.getLogger(BonusResource.class);

    private static final String ENTITY_NAME = "bonus";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final BonusService bonusService;

    private final BonusRepository bonusRepository;

    private final CourseRepository courseRepository;

    private final ExamRepository examRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final AuthorizationCheckService authCheckService;

    public BonusResource(BonusService bonusService, BonusRepository bonusRepository, CourseRepository courseRepository, ExamRepository examRepository,
            GradingScaleRepository gradingScaleRepository, AuthorizationCheckService authCheckService) {
        this.bonusService = bonusService;
        this.bonusRepository = bonusRepository;
        this.courseRepository = courseRepository;
        this.examRepository = examRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.authCheckService = authCheckService;
    }

    /**
     * TODO: Ata
     * GET /courses/{courseId}/bonus : Find bonus source for course
     *
     * @param courseId the course to which the bonus source belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus source if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> getBonusWithSourceCourse(@PathVariable Long courseId) {
        log.debug("REST request to get bonus source for course: {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        Optional<Bonus> bonus = bonusRepository.findBySourceCourseId(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return bonus.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(null));
    }

    /**
     * TODO: Ata
     * GET /courses/{courseId}/exams/{examId}/bonus : Find bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the bonus source belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus source if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Set<Bonus>> getBonusForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get bonus sources for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        Set<Bonus> bonus = bonusRepository.findAllByTargetExamId(examId);
        return ResponseEntity.ok(bonus);
    }

    /**
     * GET bonus/:bonusId : get the bonus with id.
     *
     * @param bonusId the id of the bonus to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the bonus, or with status 404 (Not Found)
     */
    @GetMapping("bonus/{bonusId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> getBonus(@PathVariable Long bonusId) {
        log.debug("REST request to get Bonus : {}", bonusId);
        Bonus bonus = bonusRepository.findById(bonusId).orElseThrow();
        GradingScale gradingScale = gradingScaleRepository.findByBonusFromId(bonus.getId()).orElseThrow();
        checkIsAtLeastInstructorForGradingScaleCourse(gradingScale);

        return ResponseEntity.ok().body(bonus);
    }

    private String calculateGradeWithBonus(BonusStrategy bonusStrategy, Double calculationSign, Double targetPoints, Double sourcePoints, GradingScale targetGradingScale,
            GradingScale sourceGradingScale) {
        checkIsAtLeastInstructorForGradingScaleCourse(targetGradingScale);
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);

        return bonusService.calculateGradeWithBonus(bonusStrategy, targetGradingScale, targetPoints, sourceGradingScale, sourcePoints, calculationSign);
    }

    @GetMapping("bonus/calculate-raw")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<String> calculateGradeWithBonus(@RequestParam BonusStrategy bonusStrategy, @RequestParam Double calculationSign, @RequestParam Long targetGradingScaleId,
            @RequestParam Double targetPoints, @RequestParam Long sourceGradingScaleId, @RequestParam Double sourcePoints) {

        // TODO: Ata: Add auth and validation.
        var targetGradingScale = gradingScaleRepository.findById(targetGradingScaleId).orElseThrow();
        var sourceGradingScale = gradingScaleRepository.findById(sourceGradingScaleId).orElseThrow();

        String gradeWithBonus = calculateGradeWithBonus(bonusStrategy, calculationSign, targetPoints, sourcePoints, targetGradingScale, sourceGradingScale);

        return ResponseEntity.ok(gradeWithBonus);
    }

    // @GetMapping("bonus/{bonusId}/calculate-grade/{studentId}")
    // @PreAuthorize("hasRole('USER')")
    // public ResponseEntity<String> calculateGradeWithBonus(@PathVariable Long bonusId, @PathVariable Long studentId) {
    // log.debug("REST request to get Bonus : {} for Student Id: {}", bonusId, studentId);
    // Bonus bonus = bonusRepository.findById(bonusId).orElseThrow();
    // var targetGradingScale = gradingScaleRepository.findByBonusFromId(bonusId).orElseThrow();
    // var sourceGradingScale = bonus.getSource();
    //
    // String gradeWithBonus = calculateGradeWithBonus(bonus.getBonusStrategy(), bonus.getCalculationSign(), targetPoints, sourcePoints, targetGradingScale, sourceGradingScale);
    //
    // return ResponseEntity.ok(gradeWithBonus);
    // }

    // private void validateBonus(Optional<Bonus> existingBonus, Bonus bonus) {
    // if (existingBonus.isPresent()) {
    // throw new BadRequestAlertException("A bonus source already exists", ENTITY_NAME, "bonusAlreadyExists");
    // }
    // else if (bonus.getGradeSteps() == null || bonus.getGradeSteps().isEmpty()) {
    // throw new BadRequestAlertException("A bonus source must contain grade steps", ENTITY_NAME, "emptyGradeSteps");
    // }
    // else if (bonus.getId() != null) {
    // throw new BadRequestAlertException("A bonus source can't contain a predefined id", ENTITY_NAME, "bonusHasId");
    // }
    // }

    /**
     * POST /courses/{courseId}/exams/{examId}/bonus : Create bonus source for exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId the exam to which the bonus source belongs
     * @param bonus the bonus source which will be created
     * @return ResponseEntity with status 201 (Created) with body the new bonus source if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> createBonusForTargetExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody Bonus bonus) throws URISyntaxException {
        log.debug("REST request to create a bonus source for exam: {}", examId);
        if (bonus.getId() != null) {
            throw new BadRequestAlertException("A new bonus source cannot already have an ID", ENTITY_NAME, "idexists");
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        // Optional<Bonus> existingBonus = bonusRepository.findBySourceExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        // TODO: Ata: Check source course too (see update method)

        // validateBonus(existingBonus, bonus);
        GradingScale examGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(examId).orElseThrow();

        // bonus.setTarget(examGradingScale);
        examGradingScale.getBonusFrom().add(bonus);

        Bonus savedBonus = bonusService.saveBonus(bonus);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + savedBonus.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonus);
    }

    /**
     * PUT /courses/{courseId}/bonus : Update bonus source for course
     *
     * @param bonus the bonus source which will be updated
     * @return ResponseEntity with status 200 (Ok) with body the newly updated bonus source if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> updateBonus(@RequestBody Bonus bonus) {
        log.debug("REST request to update a bonus source: {}", bonus.getId());
        // Course course = courseRepository.findByIdElseThrow(courseId);
        Bonus oldBonus = bonusRepository.findById(bonus.getId()).orElseThrow();
        GradingScale gradingScale = gradingScaleRepository.findByBonusFromId(oldBonus.getId()).orElseThrow();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, gradingScale.getExam().getCourse(), null);

        GradingScale sourceGradingScale = oldBonus.getSource();
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);

        Bonus savedBonus = bonusService.saveBonus(bonus);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonus);
    }

    private void checkIsAtLeastInstructorForGradingScaleCourse(GradingScale gradingScale) {
        Course sourceCourse = gradingScale.getExam() != null ? gradingScale.getExam().getCourse() : gradingScale.getCourse();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sourceCourse, null);
    }

    private void checkIsAtLeastStudentForGradingScaleCourse(GradingScale gradingScale) {
        Course sourceCourse = gradingScale.getExam() != null ? gradingScale.getExam().getCourse() : gradingScale.getCourse();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, sourceCourse, null);
    }
    // /**
    // * PUT /courses/{courseId}/exams/{examId}/bonus : Update bonus source for exam
    // *
    // * @param courseId the course to which the exam belongs
    // * @param examId the exam to which the bonus source belongs
    // * @param bonus the bonus source which will be updated
    // * @return ResponseEntity with status 200 (Ok) with body the newly updated bonus source if it is correctly formatted and 400 (Bad request) otherwise
    // */
    // @PutMapping("/courses/{courseId}/exams/{examId}/bonus")
    // @PreAuthorize("hasRole('INSTRUCTOR')")
    // public ResponseEntity<Bonus> updateBonusForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody Bonus bonus) {
    // log.debug("REST request to update a bonus source for exam: {}", examId);
    // Course course = courseRepository.findByIdElseThrow(courseId);
    // Exam exam = examRepository.findByIdElseThrow(examId);
    // Bonus oldBonus = bonusRepository.findByExamIdOrElseThrow(examId);
    // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // bonus.setId(oldBonus.getId());
    // if (bonus.getExam().getMaxPoints() != exam.getMaxPoints()) {
    // exam.setMaxPoints(bonus.getExam().getMaxPoints());
    // examRepository.save(exam);
    // }
    // bonus.setExam(exam);
    // Bonus savedBonus = bonusService.saveBonus(bonus);
    // return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonus);
    // }

    /**
     * DELETE /courses/{courseId}/bonus : Delete bonus source for course
     *
     * @param bonusId the id of the bonus source to delete
     * @return ResponseEntity with status 200 (Ok) if the bonus source is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/bonus/{bonusId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteBonus(@PathVariable Long bonusId) {
        log.debug("REST request to delete the bonus source: {}", bonusId);
        Bonus bonus = bonusRepository.findById(bonusId).orElseThrow();
        checkIsAtLeastInstructorForGradingScaleCourse(bonus.getTarget());
        bonusRepository.delete(bonus);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    // /**
    // * DELETE /courses/{courseId}/exams/{examId}/bonus : Delete bonus source for course
    // *
    // * @param courseId the course to which the exam belongs
    // * @param examId the exam to which the bonus source belongs
    // * @return ResponseEntity with status 200 (Ok) if the bonus source is successfully deleted and 400 (Bad request) otherwise
    // */
    // @DeleteMapping("/courses/{courseId}/exams/{examId}/bonus")
    // @PreAuthorize("hasRole('INSTRUCTOR')")
    // public ResponseEntity<Void> deleteBonusForExam(@PathVariable Long courseId, @PathVariable Long examId) {
    // log.debug("REST request to delete the bonus source for exam: {}", examId);
    // Course course = courseRepository.findByIdElseThrow(courseId);
    // Bonus bonus = bonusRepository.findByExamIdOrElseThrow(examId);
    // authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
    // bonusRepository.delete(bonus);
    // return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    // }

}
