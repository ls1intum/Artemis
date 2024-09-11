package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.Bonus;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.repository.BonusRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.service.BonusService;
import de.tum.cit.aet.artemis.assessment.service.CourseScoreCalculationService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.web.rest.dto.BonusExampleDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;

/**
 * REST controller for managing bonus
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class BonusResource {

    private static final Logger log = LoggerFactory.getLogger(BonusResource.class);

    private static final String ENTITY_NAME = "bonus";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final BonusService bonusService;

    private final BonusRepository bonusRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final AuthorizationCheckService authCheckService;

    private final ExamAccessService examAccessService;

    private final CourseScoreCalculationService courseScoreCalculationService;

    private final CourseRepository courseRepository;

    public BonusResource(BonusService bonusService, BonusRepository bonusRepository, GradingScaleRepository gradingScaleRepository, AuthorizationCheckService authCheckService,
            ExamAccessService examAccessService, CourseScoreCalculationService courseScoreCalculationService, CourseRepository courseRepository) {
        this.bonusService = bonusService;
        this.bonusRepository = bonusRepository;
        this.gradingScaleRepository = gradingScaleRepository;
        this.authCheckService = authCheckService;
        this.examAccessService = examAccessService;
        this.courseScoreCalculationService = courseScoreCalculationService;
        this.courseRepository = courseRepository;
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/bonus : Find bonus model for exam (where Bonus.bonusToGradingScale corresponds to the exam)
     * Sets Bonus.bonusStrategy from the bonus strategy set on the exam's grading scale.
     *
     * @param courseId                the course to which the exam belongs
     * @param examId                  the exam to which the bonus belongs
     * @param includeSourceGradeSteps flag to determine if the GradeSteps for the source grading scale should be included in the response. Default is false.
     * @return ResponseEntity with status 200 (Ok) with body the bonus if it exists and 404 (Not found) otherwise
     */
    @GetMapping("courses/{courseId}/exams/{examId}/bonus")
    @EnforceAtLeastStudent
    public ResponseEntity<Bonus> getBonusForExam(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam(required = false) boolean includeSourceGradeSteps) {
        log.debug("REST request to get bonus for exam: {}", examId);
        examAccessService.checkCourseAndExamAccessForStudentElseThrow(courseId, examId);

        var bonus = bonusRepository.findAllByBonusToExamId(examId).stream().findAny().orElseThrow(() -> new EntityNotFoundException("BonusToGradingScale exam", examId));
        bonus.setBonusStrategy(bonus.getBonusToGradingScale().getBonusStrategy());
        filterBonusForResponse(bonus, includeSourceGradeSteps);

        GradingScale sourceGradingScale = bonus.getSourceGradingScale();
        if (sourceGradingScale != null && sourceGradingScale.getCourse() != null) {
            sourceGradingScale.getCourse().setMaxPoints((int) getSourceReachablePoints(sourceGradingScale));
        }

        return ResponseEntity.ok(bonus);
    }

    private BonusExampleDTO calculateGradeWithBonus(BonusStrategy bonusStrategy, Double calculationSign, Double bonusToAchievedPoints, Double sourceAchievedPoints,
            Double sourceReachablePoints, GradingScale bonusToGradingScale, GradingScale sourceGradingScale) {
        checkIsAtLeastInstructorForGradingScaleCourse(sourceGradingScale);

        return bonusService.calculateGradeWithBonus(bonusStrategy, bonusToGradingScale, bonusToAchievedPoints, sourceGradingScale, sourceAchievedPoints, sourceReachablePoints,
                calculationSign);
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
    @GetMapping("courses/{courseId}/exams/{examId}/bonus/calculate-raw")
    @EnforceAdmin
    // TODO: Remove the manual configuration once the endpoint gets it's final pre-authorization when the feature releases.
    @ManualConfig
    public ResponseEntity<BonusExampleDTO> calculateGradeWithBonus(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam BonusStrategy bonusStrategy,
            @RequestParam Double calculationSign, @RequestParam Double bonusToPoints, @RequestParam Long sourceGradingScaleId, @RequestParam Double sourcePoints) {

        // TODO: Add auth and validation and authorize to USER role. Currently enabled only to ADMINs for testing.
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByExamId(examId).orElseThrow();
        var sourceGradingScale = gradingScaleRepository.findById(sourceGradingScaleId).orElseThrow();

        double sourceReachablePoints = getSourceReachablePoints(sourceGradingScale);

        BonusExampleDTO gradeWithBonus = calculateGradeWithBonus(bonusStrategy, calculationSign, bonusToPoints, sourcePoints, sourceReachablePoints, bonusToGradingScale,
                sourceGradingScale);
        return ResponseEntity.ok(gradeWithBonus);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/bonus : Create bonus for an exam
     *
     * @param courseId the course to which the exam belongs
     * @param examId   the exam to which the bonus belongs
     * @param bonus    the bonus which will be created
     * @return ResponseEntity with status 201 (Created) with body the new bonus if no such exists for the course
     *         and if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PostMapping("courses/{courseId}/exams/{examId}/bonus")
    @EnforceAtLeastInstructor
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
     * PUT /courses/{courseId}/exams/{examId}/bonus/{bonusId} : Update updatedBonus applying to exam
     *
     * @param courseId     the course to which the exam belongs
     * @param examId       the exam to which the updatedBonus belongs
     * @param updatedBonus the updatedBonus which will be updated
     * @param bonusId      the id of the updatedBonus to update
     * @return ResponseEntity with status 200 (Ok) with body the newly updated updatedBonus if it is correctly formatted and 400 (Bad request) otherwise
     */
    @PutMapping("courses/{courseId}/exams/{examId}/bonus/{bonusId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Bonus> updateBonus(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long bonusId, @RequestBody Bonus updatedBonus) {
        log.debug("REST request to update a updatedBonus: {}", bonusId);

        if (!Objects.equals(updatedBonus.getId(), bonusId)) {
            throw new ConflictException("The updatedBonus id in the body and path do not match", ENTITY_NAME, "bonusIdMismatch");
        }

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        Bonus oldBonus = bonusRepository.findByIdElseThrow(updatedBonus.getId());
        checkBonusAppliesToExam(oldBonus, examId);

        GradingScale bonusToGradingScale = gradingScaleRepository.findWithEagerBonusFromByBonusFromId(oldBonus.getId())
                .orElseThrow(() -> new EntityNotFoundException("Grading Scale From Bonus", updatedBonus.getId()));

        boolean isSourceGradeScaleUpdated = false;
        if (updatedBonus.getSourceGradingScale() != null && !oldBonus.getSourceGradingScale().getId().equals(updatedBonus.getSourceGradingScale().getId())) {
            var sourceFromDb = gradingScaleRepository.findById(updatedBonus.getSourceGradingScale().getId()).orElseThrow();
            updatedBonus.setSourceGradingScale(sourceFromDb);
            checkIsAtLeastInstructorForGradingScaleCourse(sourceFromDb);
            isSourceGradeScaleUpdated = true;
        }

        bonusToGradingScale.addBonusFrom(updatedBonus);
        bonusToGradingScale.setBonusStrategy(updatedBonus.getBonusStrategy());
        gradingScaleRepository.save(bonusToGradingScale);
        Bonus savedBonus = bonusService.saveBonus(updatedBonus, isSourceGradeScaleUpdated);

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
    @DeleteMapping("courses/{courseId}/exams/{examId}/bonus/{bonusId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteBonus(@PathVariable Long courseId, @PathVariable Long examId, @PathVariable Long bonusId) {
        log.debug("REST request to delete the bonus: {}", bonusId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Bonus bonus = bonusRepository.findByIdElseThrow(bonusId);
        checkBonusAppliesToExam(bonus, examId);

        bonusRepository.delete(bonus);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, "")).build();
    }

    /**
     * Calculates the reachable points for the source grading scale.
     * <br>
     * If the source grading scale is an exam grading scale, the reachable points are the max points of the exam.
     * If the source grading scale is a course grading scale, the reachable points are the sum of all exercise points (excluding optional and bonus points).
     * <br>
     * Note: The reachable points need to be calculated since the {@link Course#getMaxPoints()} method might return a value different to the actual achievable points of a course.
     * This is only relevant for courses, since the instructors should be able to change the exam's max points and thereby the exam grades.
     *
     * @param sourceGradingScale the source grading scale
     * @return the reachable points of a course or the max points of an exam
     */
    private double getSourceReachablePoints(GradingScale sourceGradingScale) {
        if (sourceGradingScale == null) {
            return 0.0;
        }

        double sourceReachablePoints = sourceGradingScale.getMaxPoints();
        if (sourceGradingScale.getCourse() != null) {
            // fetch course with exercises to calculate reachable points
            Course course = courseRepository.findWithEagerExercisesById(sourceGradingScale.getCourse().getId());
            sourceReachablePoints = courseScoreCalculationService.calculateReachablePoints(sourceGradingScale, course.getExercises());
        }
        return sourceReachablePoints;
    }

}
