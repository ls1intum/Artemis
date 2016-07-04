package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.service.BambooService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for managing Result.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    @Inject
    private ResultRepository resultRepository;

    @Inject
    private ParticipationService participationService;

    @Inject
    private BambooService bambooService;

    /**
     * POST  /results : Create a new result.
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        if (result.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("result", "idexists", "A new result cannot already have an ID")).body(null);
        }
        Result savedResult = resultRepository.save(result);
        return ResponseEntity.created(new URI("/api/results/" + savedResult.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("result", savedResult.getId().toString()))
            .body(savedResult);
    }

    /**
     * POST  /results/:planKey : Notify the application about a new build result.
     *
     * @param planKey the plan key of the plan which is notifying about a new result
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/results/{planKey}",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<?> notifyResult(@PathVariable("planKey") String planKey) {
        if (planKey.contains("base")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        // Plan key has format PROJECTKEY-USERNAME, e.g. EIST16W1-ga56hur
        String projectKey = planKey.split("-")[0];
        String username = planKey.split("-")[1];

        Participation participation = participationService.findOneByExerciseProjectKeyAndStudentLogin(projectKey, username);
        if (Optional.ofNullable(participation).isPresent()) {
            bambooService.retrieveAndSaveBuildResult(projectKey + "-" + username, participation);
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * PUT  /results : Updates an existing result.
     *
     * @param result the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result,
     * or with status 400 (Bad Request) if the result is not valid,
     * or with status 500 (Internal Server Error) if the result couldnt be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        if (result.getId() == null) {
            return createResult(result);
        }
        Result savedResult = resultRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("result", savedResult.getId().toString()))
            .body(savedResult);
    }

    /**
     * GET  /results : get all the results.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Result> getAllResults() {
        log.debug("REST request to get all Results");
        List<Result> results = resultRepository.findAll();
        return results;
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participations/:participationId/results : get all the results for "id" participation.
     *
     * @param courseId        only included for API consistency, not actually used
     * @param exerciseId      only included for API consistency, not actually used
     * @param participationId the id of the participation for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations/{participationId}/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Timed
    public List<Result> getResultsForParticipation(@PathVariable Long courseId,
                                                   @PathVariable Long exerciseId,
                                                   @PathVariable Long participationId) {
        log.debug("REST request to get Results for Participation : {}", participationId);
        List<Result> results = resultRepository.findByParticipationIdOrderByBuildCompletionDateDesc(participationId);
        return results;
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/results : get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param courseId only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Result> getResultsForExercise(@PathVariable Long courseId,
                                              @PathVariable Long exerciseId,
                                              @RequestParam(defaultValue = "false") boolean showAllResults) {
        log.debug("REST request to get Results for Exercise : {}", exerciseId);
        List<Result> results;
        if (showAllResults) {
            results = resultRepository.findLatestResultsForExercise(exerciseId);
        } else {
            results = resultRepository.findEarliestSuccessfulResultsForExercise(exerciseId);
        }
        return results;
    }

    /**
     * GET  /results/:id : get the "id" result.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/results/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Result> getResult(@PathVariable Long id) {
        log.debug("REST request to get Result : {}", id);
        Result result = resultRepository.findOne(id);
        return Optional.ofNullable(result)
            .map(foundResult -> new ResponseEntity<>(
                foundResult,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * GET  /results/:id/details : get the build result details from Bamboo for the "id" result.
     *
     * @param id the id of the result to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the result, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/results/{id}/details",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Timed
    public ResponseEntity<?> getResultDetails(@PathVariable Long id, @RequestParam(required = false) String username, Principal principal) {
        log.debug("REST request to get Result : {}", id);
        Result result = resultRepository.findOne(id);
        String planSlug = username != null ? username : principal.getName();
        String planKey = result.getParticipation().getExercise().getBaseProjectKey() + "-" + planSlug;
        Map details = bambooService.retrieveLatestBuildResultDetails(planKey);
        return Optional.ofNullable(details.get("details"))
            .map(resultDetails -> new ResponseEntity<>(
                details.get("details"),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /results/:id : delete the "id" result.
     *
     * @param id the id of the result to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/results/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);
        resultRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("result", id.toString())).build();
    }

}
