package de.tum.cit.aet.artemis.proof.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
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

    public ProofExerciseResource(ProofExerciseRepository proofExerciseRepository, ProofExerciseImportService proofExerciseImportService) {
        this.proofExerciseRepository = proofExerciseRepository;
        this.proofExerciseImportService = proofExerciseImportService;
    }

    @PostMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExercise> createProofExercise(@RequestBody ProofExercise proofExercise) throws URISyntaxException {
        log.debug("REST request to save ProofExercise : {}", proofExercise);
        if (proofExercise.getId() != null) {
            throw new BadRequestAlertException("A new proof exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProofExercise result = proofExerciseRepository.save(proofExercise);
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }

    @PutMapping("proof-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExercise> updateProofExercise(@RequestBody ProofExercise proofExercise) throws URISyntaxException {
        log.debug("REST request to update ProofExercise : {}", proofExercise);
        if (proofExercise.getId() == null) {
            return createProofExercise(proofExercise);
        }
        ProofExercise result = proofExerciseRepository.save(proofExercise);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, proofExercise.getId().toString())).body(result);
    }

    @GetMapping("courses/{courseId}/proof-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<ProofExercise>> getProofExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProofExercises for course : {}", courseId);
        List<ProofExercise> exercises = proofExerciseRepository.findByCourseId(courseId);
        return ResponseEntity.ok(exercises);
    }

    @GetMapping("proof-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<ProofExercise> getProofExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get ProofExercise : {}", exerciseId);
        ProofExercise proofExercise = proofExerciseRepository.findById(exerciseId).orElseThrow();
        return ResponseEntity.ok(proofExercise);
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
    public ResponseEntity<SearchResultPageDTO<ProofExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        log.debug("REST request to get all ProofExercises on page");
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        Page<ProofExercise> exercisePage = proofExerciseRepository.findAll(pageable);
        return ResponseEntity.ok(new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages()));
    }

    /**
     * POST /proof-exercises/import/:sourceExerciseId : import a proof exercise from an existing one
     *
     * @param sourceExerciseId the id of the proof exercise to import
     * @param importedExercise the proof exercise to import
     * @return the ResponseEntity with status 201 (Created) and with body the new proof exercise, or with status 400 (Bad Request) if the proof exercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("proof-exercises/import/{sourceExerciseId}")
    @EnforceAtLeastEditor
    public ResponseEntity<ProofExercise> importExercise(@PathVariable Long sourceExerciseId, @RequestBody ProofExercise importedExercise) throws URISyntaxException {
        log.debug("REST request to import ProofExercise from {} : {}", sourceExerciseId, importedExercise);
        if (importedExercise.getId() != null) {
            throw new BadRequestAlertException("A new proof exercise cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProofExercise sourceExercise = proofExerciseRepository.findById(sourceExerciseId).orElseThrow();
        ProofExercise result = proofExerciseImportService.importProofExercise(sourceExercise, importedExercise);
        return ResponseEntity.created(new URI("/api/proof/proof-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getTitle())).body(result);
    }
}
