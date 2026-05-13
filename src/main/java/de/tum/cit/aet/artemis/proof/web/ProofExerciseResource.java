package de.tum.cit.aet.artemis.proof.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
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

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.dto.ProofExerciseDTO;
import de.tum.cit.aet.artemis.proof.repository.ProofExerciseRepository;
import de.tum.cit.aet.artemis.proof.service.ProofExerciseImportService;

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

    public ProofExerciseResource(ProofExerciseRepository proofExerciseRepository, ProofExerciseImportService proofExerciseImportService, CourseRepository courseRepository) {
        this.proofExerciseRepository = proofExerciseRepository;
        this.proofExerciseImportService = proofExerciseImportService;
        this.courseRepository = courseRepository;
    }

    @PostMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> createProofExercise(@RequestBody ProofExerciseDTO proofExerciseDTO) throws URISyntaxException {
        log.debug("REST request to create ProofExercise : {}", proofExerciseDTO);
        if (proofExerciseDTO.id() != null) {
            throw new BadRequestAlertException("A new proof exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProofExercise exercise = new ProofExercise();
        proofExerciseDTO.applyToEntity(exercise);
        if (proofExerciseDTO.courseId() != null) {
            Course course = courseRepository.findByIdElseThrow(proofExerciseDTO.courseId());
            exercise.setCourse(course);
        }
        ProofExercise saved = proofExerciseRepository.findByIdWithCategories(proofExerciseRepository.save(exercise).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + saved.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, saved.getTitle())).body(ProofExerciseDTO.of(saved));
    }

    @PutMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExerciseDTO> updateProofExercise(@RequestBody ProofExerciseDTO proofExerciseDTO) throws URISyntaxException {
        log.debug("REST request to update ProofExercise : {}", proofExerciseDTO);
        if (proofExerciseDTO.id() == null) {
            return createProofExercise(proofExerciseDTO);
        }
        ProofExercise existing = proofExerciseRepository.findByIdWithCategories(proofExerciseDTO.id()).orElseThrow();
        proofExerciseDTO.applyToEntity(existing);
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
        ProofExercise exercise = proofExerciseRepository.findByIdWithCategories(exerciseId).orElseThrow();
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
     * GET /proof-exercises : get all the proof exercises.
     *
     * @param search         the pagination information
     * @param isCourseFilter whether to filter for course exercises
     * @param isExamFilter   whether to filter for exam exercises
     * @return the ResponseEntity with status 200 (OK) and the list of proof exercises in body
     */
    @GetMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<ProofExerciseDTO>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        log.debug("REST request to get all ProofExercises on page");
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Page<ProofExercise> exercisePage = proofExerciseRepository.findAll(pageable);
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
        if (importedExerciseDTO.courseId() != null) {
            Course course = courseRepository.findByIdElseThrow(importedExerciseDTO.courseId());
            target.setCourse(course);
        }
        ProofExercise result = proofExerciseRepository.findByIdWithCategories(proofExerciseImportService.importProofExercise(sourceExercise, target).getId()).orElseThrow();
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(ProofExerciseDTO.of(result));
    }
}
