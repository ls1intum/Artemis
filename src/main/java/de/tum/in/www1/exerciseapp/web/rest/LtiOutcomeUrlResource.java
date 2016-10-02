package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.LtiOutcomeUrl;
import de.tum.in.www1.exerciseapp.repository.LtiOutcomeUrlRepository;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
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
        
    @Inject
    private LtiOutcomeUrlRepository ltiOutcomeUrlRepository;
    
    /**
     * POST  /lti-outcome-urls : Create a new ltiOutcomeUrl.
     *
     * @param ltiOutcomeUrl the ltiOutcomeUrl to create
     * @return the ResponseEntity with status 201 (Created) and with body the new ltiOutcomeUrl, or with status 400 (Bad Request) if the ltiOutcomeUrl has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/lti-outcome-urls",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<LtiOutcomeUrl> createLtiOutcomeUrl(@RequestBody LtiOutcomeUrl ltiOutcomeUrl) throws URISyntaxException {
        log.debug("REST request to save LtiOutcomeUrl : {}", ltiOutcomeUrl);
        if (ltiOutcomeUrl.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("ltiOutcomeUrl", "idexists", "A new ltiOutcomeUrl cannot already have an ID")).body(null);
        }
        LtiOutcomeUrl result = ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
        return ResponseEntity.created(new URI("/api/lti-outcome-urls/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("ltiOutcomeUrl", result.getId().toString()))
            .body(result);
    }

    /**
     * PUT  /lti-outcome-urls : Updates an existing ltiOutcomeUrl.
     *
     * @param ltiOutcomeUrl the ltiOutcomeUrl to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated ltiOutcomeUrl,
     * or with status 400 (Bad Request) if the ltiOutcomeUrl is not valid,
     * or with status 500 (Internal Server Error) if the ltiOutcomeUrl couldnt be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/lti-outcome-urls",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<LtiOutcomeUrl> updateLtiOutcomeUrl(@RequestBody LtiOutcomeUrl ltiOutcomeUrl) throws URISyntaxException {
        log.debug("REST request to update LtiOutcomeUrl : {}", ltiOutcomeUrl);
        if (ltiOutcomeUrl.getId() == null) {
            return createLtiOutcomeUrl(ltiOutcomeUrl);
        }
        LtiOutcomeUrl result = ltiOutcomeUrlRepository.save(ltiOutcomeUrl);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("ltiOutcomeUrl", ltiOutcomeUrl.getId().toString()))
            .body(result);
    }

    /**
     * GET  /lti-outcome-urls : get all the ltiOutcomeUrls.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of ltiOutcomeUrls in body
     */
    @RequestMapping(value = "/lti-outcome-urls",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<LtiOutcomeUrl> getAllLtiOutcomeUrls() {
        log.debug("REST request to get all LtiOutcomeUrls");
        List<LtiOutcomeUrl> ltiOutcomeUrls = ltiOutcomeUrlRepository.findAll();
        return ltiOutcomeUrls;
    }

    /**
     * GET  /lti-outcome-urls/:id : get the "id" ltiOutcomeUrl.
     *
     * @param id the id of the ltiOutcomeUrl to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the ltiOutcomeUrl, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/lti-outcome-urls/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<LtiOutcomeUrl> getLtiOutcomeUrl(@PathVariable Long id) {
        log.debug("REST request to get LtiOutcomeUrl : {}", id);
        LtiOutcomeUrl ltiOutcomeUrl = ltiOutcomeUrlRepository.findOne(id);
        return Optional.ofNullable(ltiOutcomeUrl)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /lti-outcome-urls/:id : delete the "id" ltiOutcomeUrl.
     *
     * @param id the id of the ltiOutcomeUrl to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/lti-outcome-urls/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteLtiOutcomeUrl(@PathVariable Long id) {
        log.debug("REST request to delete LtiOutcomeUrl : {}", id);
        ltiOutcomeUrlRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("ltiOutcomeUrl", id.toString())).build();
    }

}
