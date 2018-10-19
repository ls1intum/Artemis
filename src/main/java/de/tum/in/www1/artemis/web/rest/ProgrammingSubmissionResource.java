package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import io.github.jhipster.web.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;

import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing ProgrammingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ProgrammingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ProgrammingSubmissionResource.class);

    private static final String ENTITY_NAME = "programmingSubmission";

    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    public ProgrammingSubmissionResource(ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    /**
     * POST  /programming-submissions : Create a new programmingSubmission.
     *
     * @param programmingSubmission the programmingSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new programmingSubmission, or with status 400 (Bad Request) if the programmingSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/programming-submissions")
    @Timed
    public ResponseEntity<ProgrammingSubmission> createProgrammingSubmission(@RequestBody ProgrammingSubmission programmingSubmission) throws URISyntaxException {
        log.debug("REST request to save ProgrammingSubmission : {}", programmingSubmission);
        if (programmingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new programmingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ProgrammingSubmission result = programmingSubmissionRepository.save(programmingSubmission);
        return ResponseEntity.created(new URI("/api/programming-submissions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /programming-submissions : Updates an existing programmingSubmission.
     *
     * @param programmingSubmission the programmingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated programmingSubmission,
     * or with status 400 (Bad Request) if the programmingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the programmingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/programming-submissions")
    @Timed
    public ResponseEntity<ProgrammingSubmission> updateProgrammingSubmission(@RequestBody ProgrammingSubmission programmingSubmission) throws URISyntaxException {
        log.debug("REST request to update ProgrammingSubmission : {}", programmingSubmission);
        if (programmingSubmission.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ProgrammingSubmission result = programmingSubmissionRepository.save(programmingSubmission);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, programmingSubmission.getId().toString()))
            .body(result);
    }

    /**
     * GET  /programming-submissions : get all the programmingSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of programmingSubmissions in body
     */
    @GetMapping("/programming-submissions")
    @Timed
    public List<ProgrammingSubmission> getAllProgrammingSubmissions() {
        log.debug("REST request to get all ProgrammingSubmissions");
        return programmingSubmissionRepository.findAll();
    }

    /**
     * GET  /programming-submissions/:id : get the "id" programmingSubmission.
     *
     * @param id the id of the programmingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the programmingSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/programming-submissions/{id}")
    @Timed
    public ResponseEntity<ProgrammingSubmission> getProgrammingSubmission(@PathVariable Long id) {
        log.debug("REST request to get ProgrammingSubmission : {}", id);
        Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(programmingSubmission);
    }

    /**
     * DELETE  /programming-submissions/:id : delete the "id" programmingSubmission.
     *
     * @param id the id of the programmingSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/programming-submissions/{id}")
    @Timed
    public ResponseEntity<Void> deleteProgrammingSubmission(@PathVariable Long id) {
        log.debug("REST request to delete ProgrammingSubmission : {}", id);

        programmingSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
