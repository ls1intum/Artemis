package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;

/**
 * REST controller for managing ProgrammingExerciseTestCase.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingExerciseTestCaseResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseResource.class);

    private static final String ENTITY_NAME = "programmingExerciseTestCase";

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    public ProgrammingExerciseTestCaseResource(ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository) {
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
    }

    /**
     * POST /programming-exercise-test-cases : Create a new programmingExerciseTestCase.
     *
     * @param programmingExerciseTestCase the programmingExerciseTestCase to create
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingExerciseTestCase, or with status 400 (Bad Request) if the programmingExerciseTestCase
     *         has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/programming-exercise-test-cases")
    public ResponseEntity<ProgrammingExerciseTestCase> createProgrammingExerciseTestCase(@RequestBody ProgrammingExerciseTestCase programmingExerciseTestCase)
            throws URISyntaxException {
        log.debug("REST request to save ProgrammingExerciseTestCase : {}", programmingExerciseTestCase);
        if (programmingExerciseTestCase.getId() != null) {
            throw new BadRequestAlertException("A new programmingExerciseTestCase cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProgrammingExerciseTestCase result = programmingExerciseTestCaseRepository.save(programmingExerciseTestCase);
        return ResponseEntity.created(new URI("/api/programming-exercise-test-cases/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /programming-exercise-test-cases : Updates an existing programmingExerciseTestCase.
     *
     * @param programmingExerciseTestCase the programmingExerciseTestCase to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated programmingExerciseTestCase, or with status 400 (Bad Request) if the programmingExerciseTestCase is
     *         not valid, or with status 500 (Internal Server Error) if the programmingExerciseTestCase couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/programming-exercise-test-cases")
    public ResponseEntity<ProgrammingExerciseTestCase> updateProgrammingExerciseTestCase(@RequestBody ProgrammingExerciseTestCase programmingExerciseTestCase)
            throws URISyntaxException {
        log.debug("REST request to update ProgrammingExerciseTestCase : {}", programmingExerciseTestCase);
        if (programmingExerciseTestCase.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ProgrammingExerciseTestCase result = programmingExerciseTestCaseRepository.save(programmingExerciseTestCase);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, programmingExerciseTestCase.getId().toString())).body(result);
    }

    /**
     * GET /programming-exercise-test-cases : get all the programmingExerciseTestCases.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingExerciseTestCases in body
     */
    @GetMapping("/programming-exercise-test-cases")
    public List<ProgrammingExerciseTestCase> getAllProgrammingExerciseTestCases() {
        log.debug("REST request to get all ProgrammingExerciseTestCases");
        return programmingExerciseTestCaseRepository.findAll();
    }

    /**
     * GET /programming-exercise-test-cases/:id : get the "id" programmingExerciseTestCase.
     *
     * @param id the id of the programmingExerciseTestCase to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingExerciseTestCase, or with status 404 (Not Found)
     */
    @GetMapping("/programming-exercise-test-cases/{id}")
    public ResponseEntity<ProgrammingExerciseTestCase> getProgrammingExerciseTestCase(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingExerciseTestCase : {}", id);
        Optional<ProgrammingExerciseTestCase> programmingExerciseTestCase = programmingExerciseTestCaseRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(programmingExerciseTestCase);
    }

    /**
     * DELETE /programming-exercise-test-cases/:id : delete the "id" programmingExerciseTestCase.
     *
     * @param id the id of the programmingExerciseTestCase to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/programming-exercise-test-cases/{id}")
    public ResponseEntity<Void> deleteProgrammingExerciseTestCase(@PathVariable Long id) {
        log.debug("REST request to delete ProgrammingExerciseTestCase : {}", id);
        programmingExerciseTestCaseRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
