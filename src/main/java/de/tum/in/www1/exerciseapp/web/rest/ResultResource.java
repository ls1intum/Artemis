package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Feedback;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.Result;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.service.*;
import de.tum.in.www1.exerciseapp.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Result.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
public class ResultResource {

    private final Logger log = LoggerFactory.getLogger(ResultResource.class);

    private static final String ENTITY_NAME = "result";

    private final ResultRepository resultRepository;
    private final Optional<LtiService> ltiService;
    private final ParticipationService participationService;
    private final ResultService resultService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final FeedbackService feedbackService;

    public ResultResource(ResultRepository resultRepository, Optional<LtiService> ltiService, ParticipationService participationService, ResultService resultService, Optional<ContinuousIntegrationService> continuousIntegrationService, FeedbackService feedbackService) {

        this.resultRepository = resultRepository;
        this.ltiService = ltiService;
        this.participationService = participationService;
        this.resultService = resultService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.feedbackService = feedbackService;
    }

    /**
     * POST  /results : Create a new manual result.
     *
     * @param result the result to create
     * @return the ResponseEntity with status 201 (Created) and with body the new result, or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/results")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Result> createResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to save Result : {}", result);
        if (result.getId() != null) {
            throw new BadRequestAlertException("A new result cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Result savedResult = resultRepository.save(result);
        ltiService.ifPresent(ltiService -> ltiService.onNewBuildResult(savedResult.getParticipation()));
        return ResponseEntity.created(new URI("/api/results/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    /**
     * POST  /results/:planKey : Notify the application about a new build result for a programming exercise
     * This API is invoked by the CI Server at the end of the build/test result
     *
     * @param planKey the plan key of the plan which is notifying about a new result
     * @return the ResponseEntity with status 200 (OK), or with status 400 (Bad Request) if the result has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "/results/{planKey}")
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
            }
            else {
                log.warn("REST request for new result of overdue exercise. Participation: {}", participation);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        }
        else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }


    /**
     * PUT  /results : Updates an existing result.
     *
     * @param result the result to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated result,
     * or with status 400 (Bad Request) if the result is not valid,
     * or with status 500 (Internal Server Error) if the result couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/results")
    @Timed
    public ResponseEntity<Result> updateResult(@RequestBody Result result) throws URISyntaxException {
        log.debug("REST request to update Result : {}", result);
        if (result.getId() == null) {
            return createResult(result);
        }
        resultRepository.save(result);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    //Deactivated because it would load all (thousands) results and completely overload the server
    //TODO: activate this call again using the infinite scroll page mechanism
//    /**
//     * GET  /results : get all the results.
//     *
//     * @return the ResponseEntity with status 200 (OK) and the list of results in body
//     */
//    @GetMapping("/results")
//    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
//    @Timed
//    public List<Result> getAllResults() {
//        log.debug("REST request to get all Results");
//        return resultRepository.findAll();
//    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participations/:participationId/results : get all the results for "id" participation.
     *
     * @param courseId        only included for API consistency, not actually used
     * @param exerciseId      only included for API consistency, not actually used
     * @param participationId the id of the participation for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations/{participationId}/results")
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
                results = resultRepository.findByParticipationIdOrderByCompletionDateDesc(participationId);
            } else {
                results = resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participationId)
                .map(Arrays::asList)
                .orElse(new ArrayList<>());
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
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/results")
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


        //Each object array in the list contains two Long values, participation id (index 0) and
        //number of results for this participation (index 1)

        List<Object[]> submissionCounts = resultRepository.findSubmissionCountsForStudents(exerciseId);

        //Matches each result with the number of results in corresponding participation
        results.forEach(result ->
            submissionCounts.forEach(submissionCount -> {
                if (result.getParticipation().getId().equals(submissionCount[0])) {
                    result.setSubmissionCount((Long) submissionCount[1]);
                }
            }));

        return results;
    }

    /**
     * GET  /courses/:courseId/results : get the successful results for a course, ordered ascending by build completion date.
     *
     * @param courseId the id of the course for which to retrieve the results
     * @return the ResponseEntity with status 200 (OK) and the list of results in body
     */
    @GetMapping(value = "/courses/{courseId}/results")
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
    @GetMapping("/results/{id}")
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
    @GetMapping(value = "/results/{id}/details")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    @Transactional
    public ResponseEntity<List<Feedback>> getResultDetails(@PathVariable Long id, @RequestParam(required = false) String username, Authentication authentication) {
        log.debug("REST request to get Result : {}", id);
        Result result = resultRepository.findOne(id);
        AbstractAuthenticationToken user = (AbstractAuthenticationToken) authentication;
        GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
        GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);
        if (result.getParticipation().getStudent().getLogin().equals(user.getName()) || (user.getAuthorities().contains(adminAuthority) || user.getAuthorities().contains(taAuthority))) {
            try {
            List<Feedback> feedbacks = new ArrayList<Feedback>(feedbackService.getFeedbackForBuildResult(result));
                return Optional.ofNullable(feedbacks)
                    .map(resultDetails -> new ResponseEntity<>(feedbacks, HttpStatus.OK))
                    .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
            } catch (Exception e) {
                log.error("REST request to get Result failed : {}", id, e);
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
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
    @DeleteMapping("/results/{id}")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        log.debug("REST request to delete Result : {}", id);
        resultRepository.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(ENTITY_NAME, id.toString())).build();
    }
}
