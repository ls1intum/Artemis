package de.tum.cit.aet.artemis.math.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
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

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.math.config.MathEnabled;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathNodes;
import de.tum.cit.aet.artemis.math.dto.MathExerciseDTO;
import de.tum.cit.aet.artemis.math.dto.MathSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.math.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.service.MathExerciseImportService;
import de.tum.cit.aet.artemis.math.service.MathGradingService;

@Lazy
@Conditional(MathEnabled.class)
@RestController
@RequestMapping("api/math/")
public class MathExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(MathExerciseResource.class);

    private static final String ENTITY_NAME = "mathExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final MathExerciseRepository mathExerciseRepository;

    private final MathExerciseImportService mathExerciseImportService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final MathGradingService mathGradingService;

    private final ExerciseService exerciseService;

    public MathExerciseResource(MathExerciseRepository mathExerciseRepository, MathExerciseImportService mathExerciseImportService, CourseRepository courseRepository,
            UserRepository userRepository, ExerciseSpecificationService exerciseSpecificationService, MathGradingService mathGradingService, ExerciseService exerciseService) {
        this.mathExerciseRepository = mathExerciseRepository;
        this.mathExerciseImportService = mathExerciseImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.mathGradingService = mathGradingService;
        this.exerciseService = exerciseService;
    }

    /**
     * POST /math-exercises : create a new math exercise.
     *
     * @param mathExerciseDTO the exercise to create
     * @return the created exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PostMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> createMathExercise(@RequestBody MathExerciseDTO mathExerciseDTO) throws URISyntaxException {
        log.debug("REST request to create MathExercise : {}", mathExerciseDTO);
        if (mathExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new math exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        validateExpressionsWildcardFree(mathExerciseDTO);
        MathExercise exercise = new MathExercise();
        mathExerciseDTO.applyToEntity(exercise);
        normalizeExpressions(exercise);
        applyCourse(mathExerciseDTO, exercise);
        MathExercise saved = mathExerciseRepository.findByIdWithCategories(mathExerciseRepository.save(exercise).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/math/math-exercises/" + saved.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, saved.getTitle())).body(MathExerciseDTO.of(saved));
    }

    /**
     * PUT /math-exercises : update an existing math exercise. Delegates to create if the
     * DTO has no id, matching the behaviour of the other exercise resources.
     *
     * @param mathExerciseDTO the exercise to update
     * @return the updated exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PutMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> updateMathExercise(@RequestBody MathExerciseDTO mathExerciseDTO) throws URISyntaxException {
        log.debug("REST request to update MathExercise : {}", mathExerciseDTO);
        if (mathExerciseDTO.id() == null) {
            return createMathExercise(mathExerciseDTO);
        }
        validateExpressionsWildcardFree(mathExerciseDTO);
        MathExercise existing = mathExerciseRepository.findByIdWithCategories(mathExerciseDTO.id()).orElseThrow();
        mathExerciseDTO.applyToEntity(existing);
        normalizeExpressions(existing);
        applyCourse(mathExerciseDTO, existing);
        MathExercise saved = mathExerciseRepository.findByIdWithCategories(mathExerciseRepository.save(existing).getId()).orElseThrow();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, saved.getId().toString())).body(MathExerciseDTO.of(saved));
    }

    /**
     * PUT /math-exercises/{exerciseId}/re-evaluate : update an existing math exercise and re-evaluate
     * feedback associated with structured grading instructions. Mirrors the pattern used by the other
     * exercise resources (e.g. {@code TextExerciseCreationUpdateResource#reEvaluateAndUpdateTextExercise}).
     *
     * @param exerciseId                                  path id of the exercise to update; must match the DTO id
     * @param mathExerciseDTO                             the updated exercise payload
     * @param deleteFeedbackAfterGradingInstructionUpdate if true, drop feedback whose grading instructions were removed
     * @return the updated exercise
     */
    @PutMapping("math-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> reEvaluateAndUpdateMathExercise(@PathVariable long exerciseId, @RequestBody MathExerciseDTO mathExerciseDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate MathExercise : {}", mathExerciseDTO);
        if (mathExerciseDTO.id() == null || !mathExerciseDTO.id().equals(exerciseId)) {
            throw new BadRequestAlertException("Exercise ID in path and body must match", ENTITY_NAME, "idMismatch");
        }
        validateExpressionsWildcardFree(mathExerciseDTO);
        MathExercise existing = mathExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        mathExerciseDTO.applyToEntity(existing);
        normalizeExpressions(existing);
        applyCourse(mathExerciseDTO, existing);
        exerciseService.reEvaluateExercise(existing, Boolean.TRUE.equals(deleteFeedbackAfterGradingInstructionUpdate));
        MathExercise saved = mathExerciseRepository.findByIdWithCategories(mathExerciseRepository.save(existing).getId()).orElseThrow();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, saved.getId().toString())).body(MathExerciseDTO.of(saved));
    }

    @GetMapping("courses/{courseId}/math-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<MathExerciseDTO>> getMathExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all MathExercises for course : {}", courseId);
        List<MathExerciseDTO> exercises = mathExerciseRepository.findByCourseIdWithCategories(courseId).stream().map(MathExerciseDTO::of).toList();
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<MathExerciseDTO> getMathExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get MathExercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        return ResponseEntity.ok(MathExerciseDTO.of(exercise));
    }

    @DeleteMapping("math-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteMathExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to delete MathExercise : {}", exerciseId);
        mathExerciseRepository.deleteById(exerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).build();
    }

    /**
     * GET /math-exercises : get all the math exercises (course-only; math exercises cannot live in exams).
     *
     * @param search the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of math exercises in body
     */
    @GetMapping("math-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<MathExerciseDTO>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get all MathExercises on page");
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<MathExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(search.getSearchTerm(), true, false, user, pageable);
        Page<MathExercise> exercisePage = mathExerciseRepository.findAll(specification, pageable);
        List<MathExerciseDTO> dtos = exercisePage.getContent().stream().map(MathExerciseDTO::of).toList();
        return ResponseEntity.ok(new SearchResultPageDTO<>(dtos, exercisePage.getTotalPages()));
    }

    /**
     * POST /math-exercises/import/:sourceExerciseId : import a math exercise from an existing one
     *
     * @param sourceExerciseId    the id of the math exercise to import
     * @param importedExerciseDTO the math exercise to import
     * @return the ResponseEntity with status 201 (Created) and with body the new math exercise, or with status 400 (Bad Request) if the math exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("math-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<MathExerciseDTO> importExercise(@PathVariable Long sourceExerciseId, @RequestBody MathExerciseDTO importedExerciseDTO) throws URISyntaxException {
        log.debug("REST request to import MathExercise from {} : {}", sourceExerciseId, importedExerciseDTO);
        if (importedExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new math exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        MathExercise sourceExercise = mathExerciseRepository.findByIdWithCategories(sourceExerciseId).orElseThrow();
        MathExercise target = new MathExercise();
        importedExerciseDTO.applyToEntity(target);
        applyCourse(importedExerciseDTO, target);
        MathExercise result = mathExerciseRepository.findByIdWithCategories(mathExerciseImportService.importMathExercise(sourceExercise, target).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/math/math-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(MathExerciseDTO.of(result));
    }

    /**
     * GET /math-exercises/{exerciseId}/verify-reachability : run the configured grader's automated reachability
     * check on the exercise. For rewrite-chain exercises this runs the FORWARD_ONLY reduction strategy from the
     * source (or goal in EQUATION mode) and reports how close it gets to the target / a tautology.
     *
     * @param exerciseId the exercise to analyse
     * @return the reachability report, or 404 if the grader does not support this check
     */
    @GetMapping("math-exercises/{exerciseId}/verify-reachability")
    @EnforceAtLeastEditor
    public ResponseEntity<ReachabilityReport> verifyReachability(@PathVariable Long exerciseId) {
        log.debug("REST request to verify reachability for MathExercise : {}", exerciseId);
        MathExercise exercise = mathExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        return mathGradingService.verifyReachability(exercise).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validateExpressionsWildcardFree(MathExerciseDTO dto) {
        try {
            MathNodes.assertWildcardFree(dto.sourceExpression());
            MathNodes.assertWildcardFree(dto.targetExpression());
            if (dto.exampleDerivations() != null) {
                dto.exampleDerivations().forEach(derivation -> derivation.forEach(step -> MathNodes.assertWildcardFree(step.resultExpression())));
            }
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestAlertException(e.getMessage(), ENTITY_NAME, "wildcardNotAllowed");
        }
    }

    private void normalizeExpressions(MathExercise exercise) {
        exercise.setSourceExpression(MathNodes.normalize(exercise.getSourceExpression()));
        exercise.setTargetExpression(MathNodes.normalize(exercise.getTargetExpression()));
        if (exercise.getExampleDerivations() != null) {
            exercise.getExampleDerivations().forEach(derivation -> derivation.replaceAll(
                    step -> new DerivationStepDTO(step.id(), step.stepIndex(), step.appliedRuleId(), step.targetNodePath(), MathNodes.normalize(step.resultExpression()))));
        }
    }

    private void applyCourse(MathExerciseDTO dto, MathExercise exercise) {
        if (dto.courseId() == null) {
            throw new BadRequestAlertException("A math exercise must belong to a course", ENTITY_NAME, "courseRequired");
        }
        Course course = courseRepository.findByIdElseThrow(dto.courseId());
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
    }
}
