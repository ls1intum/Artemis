package de.tum.in.www1.artemis.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.artemis.domain.LtiOutcomeUrl;
import de.tum.in.www1.artemis.repository.LtiOutcomeUrlRepository;
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
 * REST controller for managing LtiOutcomeUrl.
 */
@RestController
@RequestMapping("/api")
public class LtiOutcomeUrlResource {

    private final Logger log = LoggerFactory.getLogger(LtiOutcomeUrlResource.class);

    private static final String ENTITY_NAME = "ltiOutcomeUrl";

    private final LtiOutcomeUrlRepository ltiOutcomeUrlRepository;

    public LtiOutcomeUrlResource(LtiOutcomeUrlRepository ltiOutcomeUrlRepository) {
        this.ltiOutcomeUrlRepository = ltiOutcomeUrlRepository;
    }

    /**
     * POST  /lti-outcome-urls : Create a new ltiOutcomeUrl.
     *
     * @param ltiOutcomeUrl the ltiOutcomeUrl to create
     * @return the ResponseEntity with status 201 (Created) and with body the new ltiOutcomeUrl, or with status 400 (Bad Request) if the ltiOutcomeUrl has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lti-outcome-urls")
    @Timed
    public ResponseEntity<LtiOutcomeUrl> createLtiOutcomeUrl(@RequestBody LtiOutcomeUrl ltiOutcomeUrl) throws URISyntaxException {
        log.debug("REST request to save LtiOutcomeUrl : {}", ltiOutcomeUrl);
        if (ltiOutcomeUrl.getId() != null) {
            throw new BadRequestAlertException("A new ltiOutcomeUrl cannot already have an ID", ENTITY_NAME, "idexists");
        }
        LtiOutcomeUrl result = ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
        return ResponseEntity.created(new URI("/api/lti-outcome-urls/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /lti-outcome-urls : Updates an existing ltiOutcomeUrl.
     *
     * @param ltiOutcomeUrl the ltiOutcomeUrl to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated ltiOutcomeUrl,
     * or with status 400 (Bad Request) if the ltiOutcomeUrl is not valid,
     * or with status 500 (Internal Server Error) if the ltiOutcomeUrl couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/lti-outcome-urls")
    @Timed
    public ResponseEntity<LtiOutcomeUrl> updateLtiOutcomeUrl(@RequestBody LtiOutcomeUrl ltiOutcomeUrl) throws URISyntaxException {
        log.debug("REST request to update LtiOutcomeUrl : {}", ltiOutcomeUrl);
        if (ltiOutcomeUrl.getId() == null) {
            return createLtiOutcomeUrl(ltiOutcomeUrl);
        }
        LtiOutcomeUrl result = ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, ltiOutcomeUrl.getId().toString()))
            .body(result);
    }

    /**
     * GET  /lti-outcome-urls : get all the ltiOutcomeUrls.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of ltiOutcomeUrls in body
     */
    @GetMapping("/lti-outcome-urls")
    @Timed
    public List<LtiOutcomeUrl> getAllLtiOutcomeUrls() {
        log.debug("REST request to get all LtiOutcomeUrls");
        return ltiOutcomeUrlRepository.findAll();
        }

    /**
     * GET  /lti-outcome-urls/:id : get the "id" ltiOutcomeUrl.
     *
     * @param id the id of the ltiOutcomeUrl to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the ltiOutcomeUrl, or with status 404 (Not Found)
     */
    @GetMapping("/lti-outcome-urls/{id}")
    @Timed
    public ResponseEntity<LtiOutcomeUrl> getLtiOutcomeUrl(@PathVariable Long id) {
        log.debug("REST request to get LtiOutcomeUrl : {}", id);
        Optional<LtiOutcomeUrl> ltiOutcomeUrl = ltiOutcomeUrlRepository.findById(id);
        return ResponseUtil.wrapOrNotFound(ltiOutcomeUrl);
    }

    /**
     * DELETE  /lti-outcome-urls/:id : delete the "id" ltiOutcomeUrl.
     *
     * @param id the id of the ltiOutcomeUrl to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/lti-outcome-urls/{id}")
    @Timed
    public ResponseEntity<Void> deleteLtiOutcomeUrl(@PathVariable Long id) {
        log.debug("REST request to delete LtiOutcomeUrl : {}", id);
        ltiOutcomeUrlRepository.deleteById(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
