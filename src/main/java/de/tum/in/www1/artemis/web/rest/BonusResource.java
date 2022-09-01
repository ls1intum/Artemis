package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

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
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
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
     * GET /courses/{courseId}/bonus : Find bonus model which has the given course as source.
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
     * GET /courses/{courseId}/exams/{examId}/bonus : Find bonus model for exam (where Bonus.bonusToGradingScale corresponds to the exam)
     * Sets Bonus.bonusStrategy from the bonus strategy set on the exam's grading scale.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus source if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> getBonusForExam(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get bonus sources for exam: {}", examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        var bonus = bonusRepository.findAllByTargetExamId(examId).stream().findAny().orElse(null);
        if (bonus != null) {
            bonus.setBonusStrategy(bonus.getBonusToGradingScale().getBonusStrategy());
        }
        filterBonusForResponse(bonus);
        return ResponseEntity.ok(bonus);
    }

    /**
     * GET bonus/:bonusId : get the bonus with id.
     * Sets Bonus.bonusStrategy from the bonus strategy set on the exam's grading scale.
     *
     * @param bonusId the id of the bonus to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the bonus, or with status 404 (Not Found)
     */
    @GetMapping("bonus/{bonusId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> getBonus(@PathVariable Long bonusId) {
        log.debug("REST request to get Bonus : {}", bonusId);
        Bonus bonus = bonusRepository.findById(bonusId).orElseThrow();
        GradingScale bonusToGradingScale = bonus.getBonusToGradingScale();
        checkIsAtLeastInstructorForGradingScaleCourse(bonusToGradingScale);
        bonus.setBonusStrategy(bonusToGradingScale.getBonusStrategy());

        filterBonusForResponse(bonus);
        return ResponseEntity.ok().body(bonus);
    }

    private BonusExampleDTO calculateGradeWithBonus(BonusStrategy bonusStrategy, Double calculationSign, Double targetPoints, Double sourcePoints, GradingScale targetGradingScale,
            GradingScale sourceGradingScale) {
        checkIsAtLeastInstructorForGradingScaleCourse(targetGradingScale);
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);

        return bonusService.calculateGradeWithBonus(bonusStrategy, targetGradingScale, targetPoints, sourceGradingScale, sourcePoints, calculationSign);
    }

    @GetMapping("bonus/calculate-raw")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<BonusExampleDTO> calculateGradeWithBonus(@RequestParam BonusStrategy bonusStrategy, @RequestParam Double calculationSign,
            @RequestParam Long targetGradingScaleId, @RequestParam Double targetPoints, @RequestParam Long sourceGradingScaleId, @RequestParam Double sourcePoints) {

        // TODO: Ata: Add auth and validation.
        var targetGradingScale = gradingScaleRepository.findById(targetGradingScaleId).orElseThrow();
        var sourceGradingScale = gradingScaleRepository.findById(sourceGradingScaleId).orElseThrow();

        BonusExampleDTO gradeWithBonus = calculateGradeWithBonus(bonusStrategy, calculationSign, targetPoints, sourcePoints, targetGradingScale, sourceGradingScale);
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
     * @param examId   the exam to which the bonus source belongs
     * @param bonus    the bonus source which will be created
     * @return ResponseEntity with status 201 (Created) with body the new bonus source if no such exists for the course
     * and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> createBonusForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody Bonus bonus) throws URISyntaxException {
        log.debug("REST request to create a bonus source for exam: {}", examId);
        if (bonus.getId() != null) {
            throw new BadRequestAlertException("A new bonus source cannot already have an ID", ENTITY_NAME, "idexists");
        }

        Course course = courseRepository.findByIdElseThrow(courseId);
        // Optional<Bonus> existingBonus = bonusRepository.findBySourceExamId(examId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        GradingScale sourceGradingScaleFromDb = gradingScaleRepository.findById(bonus.getSourceGradingScale().getId()).orElseThrow();
        bonus.setSourceGradingScale(sourceGradingScaleFromDb);
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScaleFromDb);

        // validateBonus(existingBonus, bonus);
        GradingScale bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(examId).orElseThrow();
        bonusToGradingScale.addBonusFrom(bonus);
        bonusToGradingScale.setBonusStrategy(bonus.getBonusStrategy());

        Bonus savedBonus = bonusService.saveBonus(bonus, true);
        gradingScaleRepository.save(bonusToGradingScale);

        filterBonusForResponse(savedBonus);
        return ResponseEntity.created(new URI("/api/courses/" + courseId + "/exams/" + examId + "/bonus/" + savedBonus.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonus);
    }

    /**
     * Sets redundant fields to null to save bandwidth and prevent circular dependencies.
     * <p>
     * Warning: Modifies the input argument.
     *
     * @param bonus that will be modified
     */
    private void filterBonusForResponse(Bonus bonus) {
        if (bonus == null) {
            return;
        }

        GradingScale bonusTo = bonus.getBonusToGradingScale();
        if (bonusTo != null) {
            // This line breaks the circular dependency between savedBonus and bonusToGradingScale
            bonusTo.setBonusFrom(null);
            bonusTo.setGradeSteps(null);
        }

        GradingScale source = bonus.getSourceGradingScale();
        if (source != null) {
            source.setGradeSteps(null);
        }
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
        Bonus oldBonus = bonusRepository.findById(bonus.getId()).orElseThrow(() -> new EntityNotFoundException("Bonus", bonus.getId()));
        GradingScale bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByBonusFromId(oldBonus.getId())
                .orElseThrow(() -> new EntityNotFoundException("Grading Scale From Bonus", bonus.getId()));
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, bonusToGradingScale.getExam().getCourse(), null);

        boolean isSourceGradeScaleUpdated = false;
        if (bonus.getSourceGradingScale() != null && !oldBonus.getSourceGradingScale().getId().equals(bonus.getSourceGradingScale().getId())) {
            var sourceFromDb = gradingScaleRepository.findById(bonus.getSourceGradingScale().getId()).orElseThrow();
            bonus.setSourceGradingScale(sourceFromDb);
            checkIsAtLeastInstructorForGradingScaleCourse(sourceFromDb);
            isSourceGradeScaleUpdated = true;
        }

        bonusToGradingScale.addBonusFrom(bonus);
        bonusToGradingScale.setBonusStrategy(bonus.getBonusStrategy());
        gradingScaleRepository.save(bonusToGradingScale);
        Bonus savedBonus = bonusService.saveBonus(bonus, isSourceGradeScaleUpdated);

        filterBonusForResponse(savedBonus);
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
        checkIsAtLeastInstructorForGradingScaleCourse(bonus.getBonusToGradingScale());
        bonusRepository.delete(bonus);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
