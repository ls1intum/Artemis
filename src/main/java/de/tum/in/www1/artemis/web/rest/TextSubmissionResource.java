package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.TextSubmission;
import de.tum.in.www1.artemis.repository.TextSubmissionRepository;
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
 * REST controller for managing TextSubmission.
 */
@RestController
@RequestMapping("/api")
public class TextSubmissionResource {

    private final Logger log = LoggerFactory.getLogger(TextSubmissionResource.class);

    private static final String ENTITY_NAME = "textSubmission";

    private TextSubmissionRepository textSubmissionRepository;

    public TextSubmissionResource(TextSubmissionRepository textSubmissionRepository) {
        this.textSubmissionRepository = textSubmissionRepository;
    }

    /**
     * POST  /text-submissions : Create a new textSubmission.
     *
     * @param textSubmission the textSubmission to create
     * @return the ResponseEntity with status 201 (Created) and with body the new textSubmission, or with status 400 (Bad Request) if the textSubmission has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/text-submissions")
    @Timed
    public ResponseEntity<TextSubmission> createTextSubmission(@RequestBody TextSubmission textSubmission) throws URISyntaxException {
        log.debug("REST request to save TextSubmission : {}", textSubmission);
        if (textSubmission.getId() != null) {
            throw new BadRequestAlertException("A new textSubmission cannot already have an ID", ENTITY_NAME, "idexists");
        }
        TextSubmission result = textSubmissionRepository.save(textSubmission);
        return ResponseEntity.created(new URI("/api/text-submissions/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /text-submissions : Updates an existing textSubmission.
     *
     * @param textSubmission the textSubmission to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated textSubmission,
     * or with status 400 (Bad Request) if the textSubmission is not valid,
     * or with status 500 (Internal Server Error) if the textSubmission couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/text-submissions")
    @Timed
    public ResponseEntity<TextSubmission> updateTextSubmission(@RequestBody TextSubmission textSubmission) throws URISyntaxException {
        log.debug("REST request to update TextSubmission : {}", textSubmission);
        if (textSubmission.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        TextSubmission result = textSubmissionRepository.save(textSubmission);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, textSubmission.getId().toString()))
            .body(result);
    }

    /**
     * GET  /text-submissions : get all the textSubmissions.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of textSubmissions in body
     */
    @GetMapping("/text-submissions")
    @Timed
    public List<TextSubmission> getAllTextSubmissions() {
        log.debug("REST request to get all TextSubmissions");
        return textSubmissionRepository.findAll();
    }

    /**
     * GET  /text-submissions/:id : get the "id" textSubmission.
     *
     * @param id the id of the textSubmission to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the textSubmission, or with status 404 (Not Found)
     */
    @GetMapping("/text-submissions/{id}")
    @Timed
    public ResponseEntity<TextSubmission> getTextSubmission(@PathVariable Long id) {
        log.debug("REST request to get TextSubmission : {}", id);
        Optional<TextSubmission> textSubmission = textSubmissionRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(textSubmission);
    }

    /**
     * DELETE  /text-submissions/:id : delete the "id" textSubmission.
     *
     * @param id the id of the textSubmission to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/text-submissions/{id}")
    @Timed
    public ResponseEntity<Void> deleteTextSubmission(@PathVariable Long id) {
        log.debug("REST request to delete TextSubmission : {}", id);

        textSubmissionRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
