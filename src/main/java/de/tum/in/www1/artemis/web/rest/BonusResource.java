package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

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
import de.tum.in.www1.artemis.repository.GradingScaleRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.BonusService;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.ConflictException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing bonus
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

    private final GradingScaleRepository gradingScaleRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExamAccessService examAccessService;

    public BonusResource(BonusService bonusService, BonusRepository bonusRepository, GradingScaleRepository gradingScaleRepository, AuthorizationCheckService authCheckService,
            ExamAccessService examAccessService) {
        this.bonusService = bonusService;
        this.bonusRepository = bonusRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.authCheckService = authCheckService;
        this.examAccessService = examAccessService;
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/bonus : Find bonus model for exam (where Bonus.bonusToGradingScale corresponds to the exam)
     * Sets Bonus.bonusStrategy from the bonus strategy set on the exam's grading scale.
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @return ResponseEntity with status 200 (Ok) with body the bonus if it exists and 404 (Not found) otherwise
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Bonus> getBonusForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(required = false) boolean includeSourceGradeSteps) {
        log.debug("REST request to get bonus for exam: {}", examId);
        examAccessService.checkCourseAndExamAccessForStudentElseThrow(courseId, examId);

        var bonus = bonusRepository.findAllByBonusToExamId(examId).stream().findAny().orElseThrow(() -> new EntityNotFoundException("BonusToGradingScale exam", examId));
        bonus.setBonusStrategy(bonus.getBonusToGradingScale().getBonusStrategy());
        filterBonusForResponse(bonus, includeSourceGradeSteps);
        return ResponseEntity.ok(bonus);
    }

    private BonusExampleDTO calculateGradeWithBonus(BonusStrategy bonusStrategy, Double calculationSign, Double targetPoints, Double sourcePoints, GradingScale targetGradingScale,
            GradingScale sourceGradingScale) {
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);

        return bonusService.calculateGradeWithBonus(bonusStrategy, targetGradingScale, targetPoints, sourceGradingScale, sourcePoints, calculationSign);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/bonus/calculate-raw: Endpoint to test different bonus strategies with user-defined student points.
     * Applies bonus from sourceGradingScale to bonusToGradingScale grade steps.
     *
     * @param courseId             the course to which the exam belongs
     * @param examId               the exam to which the bonus belongs
     * @param bonusStrategy        bonus strategy of bonus
     * @param calculationSign      weight of bonus, -1 or +1
     * @param bonusToPoints        points achieved by the student at the bonusTo grading scale's exam
     * @param sourceGradingScaleId id of the grading scale that will help improve the grade of bonusTo
     * @param sourcePoints         points achieved by the student at the source grading scale's course or exam
     * @return final grade and points with bonus
     */
    @GetMapping("/courses/{courseId}/exams/{examId}/bonus/calculate-raw")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BonusExampleDTO> calculateGradeWithBonus(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam BonusStrategy bonusStrategy,
            @RequestParam Double calculationSign, @RequestParam Double bonusToPoints, @RequestParam Long sourceGradingScaleId, @RequestParam Double sourcePoints) {

        // TODO: Add auth and validation and authorize to USER role. Currently enabled only to ADMINs for testing.
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(examId).orElseThrow();
        var sourceGradingScale = gradingScaleRepository.findById(sourceGradingScaleId).orElseThrow();

        BonusExampleDTO gradeWithBonus = calculateGradeWithBonus(bonusStrategy, calculationSign, bonusToPoints, sourcePoints, bonusToGradingScale, sourceGradingScale);
        return ResponseEntity.ok(gradeWithBonus);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/bonus : Create bonus for an exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @param bonus    the bonus which will be created
     * @return ResponseEntity with status 201 (Created) with body the new bonus if no such exists for the course
     * and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("/courses/{courseId}/exams/{examId}/bonus")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> createBonusForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody Bonus bonus) throws URISyntaxException {
        log.debug("REST request to create a bonus for exam: {}", examId);
        if (bonus.getId() != null) {
            throw new BadRequestAlertException("A new bonus cannot already have an ID", ENTITY_NAME, "idexists");
        }

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        GradingScale sourceGradingScaleFromDb = gradingScaleRepository.findById(bonus.getSourceGradingScale().getId()).orElseThrow();
        bonus.setSourceGradingScale(sourceGradingScaleFromDb);
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScaleFromDb);

        GradingScale bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(examId).orElseThrow();
        if (bonusRepository.existsByBonusToGradingScaleId(bonusToGradingScale.getId())) {
            // In the future, when multiple bonuses per bonusToGradingScale is supported this check should be removed.
            throw new BadRequestAlertException("A bonus is already created for this bonusToGradingScale", ENTITY_NAME, "idexists");
        }

        bonusToGradingScale.addBonusFrom(bonus);
        bonusToGradingScale.setBonusStrategy(bonus.getBonusStrategy());

        Bonus savedBonus = bonusService.saveBonus(bonus, true);
        gradingScaleRepository.save(bonusToGradingScale);

        filterBonusForResponse(savedBonus, false);
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
    private void filterBonusForResponse(Bonus bonus, boolean includeSourceGradeSteps) {
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
        if (source != null && !includeSourceGradeSteps) {
            source.setGradeSteps(null);
        }
    }

    /**
     * PUT /courses/{courseId}/exams/{examId}/bonus/{bonusId} : Update bonus applying to exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @param bonus    the bonus which will be updated
     * @param bonusId  the id of the bonus to update
     * @return ResponseEntity with status 200 (Ok) with body the newly updated bonus if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("/courses/{courseId}/exams/{examId}/bonus/{bonusId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Bonus> updateBonus(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long bonusId, @RequestBody Bonus bonus) {
        log.debug("REST request to update a bonus: {}", bonusId);

        if (!Objects.equals(bonus.getId(), bonusId)) {
            throw new ConflictException("The bonus id in the body and path do not match", ENTITY_NAME, "bonusIdMismatch");
        }

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Bonus oldBonus = bonusRepository.findByIdElseThrow(bonus.getId());
        checkBonusAppliesToExam(oldBonus, examId);

        GradingScale bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByBonusFromId(oldBonus.getId())
                .orElseThrow(() -> new EntityNotFoundException("Grading Scale From Bonus", bonus.getId()));

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

        filterBonusForResponse(savedBonus, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, "")).body(savedBonus);
    }

    private void checkIsAtLeastInstructorForGradingScaleCourse(GradingScale gradingScale) {
        Course sourceCourse = gradingScale.getCourseViaExamOrDirectly();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, sourceCourse, null);
    }

    private void checkBonusAppliesToExam(Bonus bonus, Long examId) {
        GradingScale bonusToGradingScale = bonus.getBonusToGradingScale();
        if (!bonusToGradingScale.getExam().getId().equals(examId)) {
            throw new ConflictException("The bonus does not apply to the given exam", ENTITY_NAME, "bonusExamMismatch");
        }
    }

    /**
     * DELETE /courses/{courseId}/exams/{examId}/bonus : Delete bonus applying to exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @param bonusId  the id of the bonus to delete
     * @return ResponseEntity with status 200 (Ok) if the bonus is successfully deleted and 400 (Bad request) otherwise
     */
    @DeleteMapping("/courses/{courseId}/exams/{examId}/bonus/{bonusId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteBonus(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long bonusId) {
        log.debug("REST request to delete the bonus: {}", bonusId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Bonus bonus = bonusRepository.findByIdElseThrow(bonusId);
        checkBonusAppliesToExam(bonus, examId);

        bonusRepository.delete(bonus);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

}
