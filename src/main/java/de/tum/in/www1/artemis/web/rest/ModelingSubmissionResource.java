package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.ModelingSubmission;
import de.tum.in.www1.artemis.repository.ModelingSubmissionRepository;
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
 * REST controller for managing ModelingSubmission.
 */
@RestController
@RequestMapping("/api")
public class ModelingSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(ModelingSubmissionResource.class);

    private static final String ENTITY_NAME = "modelingSubmission";

    private final ModelingSubmissionRepository modelingSubmissionRepository;

    public ModelingSubmissionResource(ModelingSubmissionRepository modelingSubmissionRepository) {
        this.modelingSubmissionRepository = modelingSubmissionRepository;
    }

    /**
     * POST  /modeling-submissions : Create a new modelingSubmission.
     *
     * @param modelingSubmission the modelingSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new modelingSubmission, or with status 400 (Bad Request) if the modelingSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/modeling-submissions")
    @Timed
    public ResponseEntity<ModelingSubmission> createModelingSubmission(@RequestBody ModelingSubmission modelingSubmission) throws URISyntaxException {
        log.debug("REST request to save ModelingSubmission : {}", modelingSubmission);
        if (modelingSubmission.getId() != null) {
            throw new BadRequestAlertException("A new modelingSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        ModelingSubmission result = modelingSubmissionRepository.save(modelingSubmission);
        return ResponseEntity.created(new URI("/api/modeling-submissions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /modeling-submissions : Updates an existing modelingSubmission.
     *
     * @param modelingSubmission the modelingSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated modelingSubmission,
     * or with status 400 (Bad Request) if the modelingSubmission is not valid,
     * or with status 500 (Internal Server Error) if the modelingSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/modeling-submissions")
    @Timed
    public ResponseEntity<ModelingSubmission> updateModelingSubmission(@RequestBody ModelingSubmission modelingSubmission) throws URISyntaxException {
        log.debug("REST request to update ModelingSubmission : {}", modelingSubmission);
        if (modelingSubmission.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        ModelingSubmission result = modelingSubmissionRepository.save(modelingSubmission);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, modelingSubmission.getId().toString()))
            .body(result);
    }

    /**
     * GET  /modeling-submissions : get all the modelingSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of modelingSubmissions in body
     */
    @GetMapping("/modeling-submissions")
    @Timed
    public List<ModelingSubmission> getAllModelingSubmissions() {
        log.debug("REST request to get all ModelingSubmissions");
        return modelingSubmissionRepository.findAll();
    }

    /**
     * GET  /modeling-submissions/:id : get the "id" modelingSubmission.
     *
     * @param id the id of the modelingSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the modelingSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/modeling-submissions/{id}")
    @Timed
    public ResponseEntity<ModelingSubmission> getModelingSubmission(@PathVariable Long id) {
        log.debug("REST request to get ModelingSubmission : {}", id);
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(modelingSubmission);
    }

    /**
     * DELETE  /modeling-submissions/:id : delete the "id" modelingSubmission.
     *
     * @param id the id of the modelingSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/modeling-submissions/{id}")
    @Timed
    public ResponseEntity<Void> deleteModelingSubmission(@PathVariable Long id) {
        log.debug("REST request to delete ModelingSubmission : {}", id);

        modelingSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
