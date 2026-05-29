package de.tum.cit.aet.artemis.proof.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
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
import de.tum.cit.aet.artemis.proof.config.ProofEnabled;
import de.tum.cit.aet.artemis.proof.domain.MathNodes;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.dto.ProofExerciseDTO;
import de.tum.cit.aet.artemis.proof.dto.ProofSubmissionDTO.DerivationStepDTO;
import de.tum.cit.aet.artemis.proof.grader.ReachabilityReport;
import de.tum.cit.aet.artemis.proof.repository.ProofExerciseRepository;
import de.tum.cit.aet.artemis.proof.service.ProofExerciseImportService;
import de.tum.cit.aet.artemis.proof.service.ProofGradingService;

@Conditional(ProofEnabled.class)
@RestController
@RequestMapping("api/proof/")
public class ProofExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(ProofExerciseResource.class);

    private static final String ENTITY_NAME = "proofExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ProofExerciseRepository proofExerciseRepository;

    private final ProofExerciseImportService proofExerciseImportService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final ProofGradingService proofGradingService;

    private final ExerciseService exerciseService;

    public ProofExerciseResource(ProofExerciseRepository proofExerciseRepository, ProofExerciseImportService proofExerciseImportService, CourseRepository courseRepository,
            UserRepository userRepository, ExerciseSpecificationService exerciseSpecificationService, ProofGradingService proofGradingService, ExerciseService exerciseService) {
        this.proofExerciseRepository = proofExerciseRepository;
        this.proofExerciseImportService = proofExerciseImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.proofGradingService = proofGradingService;
        this.exerciseService = exerciseService;
    }

    /**
     * POST /proof-exercises : create a new proof exercise.
     *
     * @param proofExerciseDTO the exercise to create
     * @return the created exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PostMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> createProofExercise(@RequestBody ProofExerciseDTO proofExerciseDTO) throws URISyntaxException {
        log.debug("REST request to create ProofExercise : {}", proofExerciseDTO);
        if (proofExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new proof exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        validateExpressionsWildcardFree(proofExerciseDTO);
        ProofExercise exercise = new ProofExercise();
        proofExerciseDTO.applyToEntity(exercise);
        normalizeExpressions(exercise);
        applyCourse(proofExerciseDTO, exercise);
        ProofExercise saved = proofExerciseRepository.findByIdWithCategories(proofExerciseRepository.save(exercise).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + saved.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, saved.getTitle())).body(ProofExerciseDTO.of(saved));
    }

    /**
     * PUT /proof-exercises : update an existing proof exercise. Delegates to create if the
     * DTO has no id, matching the behaviour of the other exercise resources.
     *
     * @param proofExerciseDTO the exercise to update
     * @return the updated exercise
     * @throws URISyntaxException if the location URI cannot be constructed
     */
    @PutMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> updateProofExercise(@RequestBody ProofExerciseDTO proofExerciseDTO) throws URISyntaxException {
        log.debug("REST request to update ProofExercise : {}", proofExerciseDTO);
        if (proofExerciseDTO.id() == null) {
            return createProofExercise(proofExerciseDTO);
        }
        validateExpressionsWildcardFree(proofExerciseDTO);
        ProofExercise existing = proofExerciseRepository.findByIdWithCategories(proofExerciseDTO.id()).orElseThrow();
        proofExerciseDTO.applyToEntity(existing);
        normalizeExpressions(existing);
        applyCourse(proofExerciseDTO, existing);
        ProofExercise saved = proofExerciseRepository.findByIdWithCategories(proofExerciseRepository.save(existing).getId()).orElseThrow();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, saved.getId().toString())).body(ProofExerciseDTO.of(saved));
    }

    /**
     * PUT /proof-exercises/{exerciseId}/re-evaluate : update an existing proof exercise and re-evaluate
     * feedback associated with structured grading instructions. Mirrors the pattern used by the other
     * exercise resources (e.g. {@code TextExerciseCreationUpdateResource#reEvaluateAndUpdateTextExercise}).
     *
     * @param exerciseId                                  path id of the exercise to update; must match the DTO id
     * @param proofExerciseDTO                            the updated exercise payload
     * @param deleteFeedbackAfterGradingInstructionUpdate if true, drop feedback whose grading instructions were removed
     * @return the updated exercise
     */
    @PutMapping("proof-exercises/{exerciseId}/re-evaluate")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> reEvaluateAndUpdateProofExercise(@PathVariable long exerciseId, @RequestBody ProofExerciseDTO proofExerciseDTO,
            @RequestParam(value = "deleteFeedback", required = false) Boolean deleteFeedbackAfterGradingInstructionUpdate) {
        log.debug("REST request to re-evaluate ProofExercise : {}", proofExerciseDTO);
        if (proofExerciseDTO.id() == null || !proofExerciseDTO.id().equals(exerciseId)) {
            throw new BadRequestAlertException("Exercise ID in path and body must match", ENTITY_NAME, "idMismatch");
        }
        validateExpressionsWildcardFree(proofExerciseDTO);
        ProofExercise existing = proofExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
        proofExerciseDTO.applyToEntity(existing);
        normalizeExpressions(existing);
        applyCourse(proofExerciseDTO, existing);
        exerciseService.reEvaluateExercise(existing, Boolean.TRUE.equals(deleteFeedbackAfterGradingInstructionUpdate));
        ProofExercise saved = proofExerciseRepository.findByIdWithCategories(proofExerciseRepository.save(existing).getId()).orElseThrow();
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, saved.getId().toString())).body(ProofExerciseDTO.of(saved));
    }

    @GetMapping("courses/{courseId}/proof-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProofExerciseDTO>> getProofExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProofExercises for course : {}", courseId);
        List<ProofExerciseDTO> exercises = proofExerciseRepository.findByCourseIdWithCategories(courseId).stream().map(ProofExerciseDTO::of).toList();
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("proof-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ProofExerciseDTO> getProofExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ProofExercise : {}", exerciseId);
        ProofExercise exercise = proofExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        return ResponseEntity.ok(ProofExerciseDTO.of(exercise));
    }

    @DeleteMapping("proof-exercises/{exerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<Void> deleteProofExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to delete ProofExercise : {}", exerciseId);
        proofExerciseRepository.deleteById(exerciseId);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, exerciseId.toString())).build();
    }

    /**
     * GET /proof-exercises : get all the proof exercises (course-only; proof exercises cannot live in exams).
     *
     * @param search the pagination information
     * @return the ResponseEntity with status 200 (OK) and the list of proof exercises in body
     */
    @GetMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProofExerciseDTO>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search) {
        log.debug("REST request to get all ProofExercises on page");
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Specification<ProofExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(search.getSearchTerm(), true, false, user, pageable);
        Page<ProofExercise> exercisePage = proofExerciseRepository.findAll(specification, pageable);
        List<ProofExerciseDTO> dtos = exercisePage.getContent().stream().map(ProofExerciseDTO::of).toList();
        return ResponseEntity.ok(new SearchResultPageDTO<>(dtos, exercisePage.getTotalPages()));
    }

    /**
     * POST /proof-exercises/import/:sourceExerciseId : import a proof exercise from an existing one
     *
     * @param sourceExerciseId    the id of the proof exercise to import
     * @param importedExerciseDTO the proof exercise to import
     * @return the ResponseEntity with status 201 (Created) and with body the new proof exercise, or with status 400 (Bad Request) if the proof exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("proof-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> importExercise(@PathVariable Long sourceExerciseId, @RequestBody ProofExerciseDTO importedExerciseDTO) throws URISyntaxException {
        log.debug("REST request to import ProofExercise from {} : {}", sourceExerciseId, importedExerciseDTO);
        if (importedExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new proof exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProofExercise sourceExercise = proofExerciseRepository.findByIdWithCategories(sourceExerciseId).orElseThrow();
        ProofExercise target = new ProofExercise();
        importedExerciseDTO.applyToEntity(target);
        applyCourse(importedExerciseDTO, target);
        ProofExercise result = proofExerciseRepository.findByIdWithCategories(proofExerciseImportService.importProofExercise(sourceExercise, target).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(ProofExerciseDTO.of(result));
    }

    /**
     * GET /proof-exercises/{exerciseId}/verify-reachability : run the configured grader's automated reachability
     * check on the exercise. For rewrite-chain exercises this runs the FORWARD_ONLY reduction strategy from the
     * source (or goal in EQUATION mode) and reports how close it gets to the target / a tautology.
     *
     * @param exerciseId the exercise to analyse
     * @return the reachability report, or 404 if the grader does not support this check
     */
    @GetMapping("proof-exercises/{exerciseId}/verify-reachability")
    @EnforceAtLeastEditor
    public ResponseEntity<ReachabilityReport> verifyReachability(@PathVariable Long exerciseId) {
        log.debug("REST request to verify reachability for ProofExercise : {}", exerciseId);
        ProofExercise exercise = proofExerciseRepository.findByIdWithCategoriesAndCourse(exerciseId).orElseThrow();
        return proofGradingService.verifyReachability(exercise).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void validateExpressionsWildcardFree(ProofExerciseDTO dto) {
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

    private void normalizeExpressions(ProofExercise exercise) {
        exercise.setSourceExpression(MathNodes.normalize(exercise.getSourceExpression()));
        exercise.setTargetExpression(MathNodes.normalize(exercise.getTargetExpression()));
        if (exercise.getExampleDerivations() != null) {
            exercise.getExampleDerivations().forEach(derivation -> derivation.replaceAll(
                    step -> new DerivationStepDTO(step.id(), step.stepIndex(), step.appliedRuleId(), step.targetNodePath(), MathNodes.normalize(step.resultExpression()))));
        }
    }

    private void applyCourse(ProofExerciseDTO dto, ProofExercise exercise) {
        if (dto.courseId() == null) {
            throw new BadRequestAlertException("A proof exercise must belong to a course", ENTITY_NAME, "courseRequired");
        }
        Course course = courseRepository.findByIdElseThrow(dto.courseId());
        exercise.setCourse(course);
        exercise.setExerciseGroup(null);
    }
}
