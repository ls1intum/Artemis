package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.service.ContinuousIntegrationService;
import de.tum.in.www1.exerciseapp.service.LtiService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.service.ResultService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.*;

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
    private ContinuousIntegrationService continuousIntegrationService;

    @Inject
    private ResultService resultService;

    @Inject
    private LtiService ltiService;

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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        if (result.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("result", "idexists", "A new result cannot already have an ID")).body(null);
        }
        Result savedResult = resultRepository.save(result);
        ltiService.onNewBuildResult(savedResult.getParticipation());
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
        Participation participation = participationService.findOneByBuildPlanId(planKey);
        if (Optional.ofNullable(participation).isPresent()) {
            if(participation.getExercise().getDueDate() == null || ZonedDateTime.now().isBefore(participation.getExercise().getDueDate()) ) {
                resultService.onResultNotified(participation);
                return ResponseEntity.ok().build();
            } else {
                log.warn("REST request for new result of overdue exercise. Participation: {}", participation);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public List<Result> getResultsForParticipation(@PathVariable Long courseId,
                                                   @PathVariable Long exerciseId,
                                                   @PathVariable Long participationId,
                                                   @RequestParam(defaultValue = "true") boolean showAllResults,
                                                   Authentication authentication) {
        log.debug("REST request to get Results for Participation : {}", participationId);
        AbstractAuthenticationToken user = (AbstractAuthenticationToken) authentication;
        GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
        GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);
        List<Result> results = new ArrayList<>();
        Participation participation = participationService.findOne(participationId);
        if (participation != null && (participation.getStudent().getLogin().equals(user.getName()) || (user.getAuthorities().contains(adminAuthority) || user.getAuthorities().contains(taAuthority)))) {
            if(showAllResults) {
                results = resultRepository.findByParticipationIdOrderByBuildCompletionDateDesc(participationId);
            } else {
                results = resultRepository.findFirstByParticipationIdOrderByBuildCompletionDateDesc(participationId)
                .map(result -> Arrays.asList(result))
                .orElse(new ArrayList<Result>());
            }
        }
        return results;
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/results : get the successful results for an exercise, ordered ascending by build completion date.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
     * GET  /courses/:courseId/results : get the successful results for a course, ordered ascending by build completion date.
     *
     * @param courseId the id of the course for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @RequestMapping(value = "/courses/{courseId}/results",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public List<Result> getResultsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get Results for Course : {}", courseId);
        List<Result> results;
        return resultRepository.findEarliestSuccessfulResultsForCourse(courseId);
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<?> getResultDetails(@PathVariable Long id, @RequestParam(required = false) String username, Authentication authentication) {
        log.debug("REST request to get Result : {}", id);
        Result result = resultRepository.findOne(id);
        AbstractAuthenticationToken user = (AbstractAuthenticationToken) authentication;
        GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
        GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);
        if (result.getParticipation().getStudent().getLogin().equals(user.getName()) || (user.getAuthorities().contains(adminAuthority) || user.getAuthorities().contains(taAuthority))) {
            Map<String, Object> details = continuousIntegrationService.getLatestBuildResultDetails(result.getParticipation());
            return Optional.ofNullable(details.get("details"))
                .map(resultDetails -> new ResponseEntity<>(
                    details.get("details"),
                    HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        } else {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
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
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);
        resultRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("result", id.toString())).build();
    }

}
