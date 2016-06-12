package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.service.BambooService;
import de.tum.in.www1.exerciseapp.service.BitbucketService;
import de.tum.in.www1.exerciseapp.service.ExerciseService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.swift.common.cli.CliClient;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for managing Participation.
 */
@RestController
@RequestMapping("/api")
public class ParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    @Inject
    private ParticipationService participationService;

    @Inject
    private ExerciseService exerciseService;

    @Inject
    private BitbucketService bitbucketService;

    @Inject
    private BambooService bambooService;

    /**
     * POST  /participations : Create a new participation.
     *
     * @param participation the participation to create
     * @return the ResponseEntity with status 201 (Created) and with body the new participation, or with status 400 (Bad Request) if the participation has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/participations",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Participation> createParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to save Participation : {}", participation);
        if (participation.getId() != null) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("participation", "idexists", "A new participation cannot already have an ID")).body(null);
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.created(new URI("/api/participations/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert("participation", result.getId().toString()))
            .body(result);
    }

    /**
     * POST  /courses/:courseId/exercises/:exerciseId/participations : start the "id" exercise for the current user.
     *
     * @param exerciseId the id of the exercise for which to init a participation
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Participation> initParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) throws URISyntaxException {
        log.debug("REST request to init Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (Optional.ofNullable(exercise).isPresent()) {

            // Fork repository
            Map forkResult = bitbucketService.forkRepository(
                exercise.getBaseProjectKey(),
                exercise.getBaseRepositorySlug(),
                principal.getName());

            // Set permissions on forked repo
            // TODO: Do some kind of error handling
            bitbucketService.giveWritePermission(exercise.getBaseProjectKey(), (String) forkResult.get("slug"), principal.getName());

            // Clone build plan
            CliClient.ExitCode cloneExitCode = bambooService.clonePlan(exercise.getBaseProjectKey(), exercise.getBaseBuildPlanSlug(), principal.getName());
            if (!cloneExitCode.equals(CliClient.ExitCode.SUCCESS)) {
                log.error("Error while cloning build plan");
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Update build plan's repository
            CliClient.ExitCode updatePlanRepoExitCode = bambooService.updatePlanRepository(
                exercise.getBaseProjectKey(),
                principal.getName(),
                exercise.getBaseRepositorySlug(),
                exercise.getBaseProjectKey(),
                (String) forkResult.get("slug"));
            if (!updatePlanRepoExitCode.equals(CliClient.ExitCode.SUCCESS)) {
                log.error("Error while updating build plan repository");
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            // Enable build plan
            CliClient.ExitCode enablePlanRepoExitCode = bambooService.enablePlan(exercise.getBaseProjectKey(), principal.getName());
            if (!enablePlanRepoExitCode.equals(CliClient.ExitCode.SUCCESS)) {
                log.error("Error while enabling build plan");
                return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }

            Participation participation = new Participation();
//            participation.setRepositorySlug((String) forkResult.get("slug"));
            participation.setCloneUrl((String) forkResult.get("cloneUrl"));
            participation.setStudent((User) principal);
            participationService.save(participation);
//            exercise.getAllParticipations().add(participation);
//            exerciseRepository.save(exercise);
            return ResponseEntity.created(new URI("/api/participations/" + participation.getId()))
                .headers(HeaderUtil.createEntityCreationAlert("participation", participation.getId().toString()))
                .body(participation);
        } else {
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    /**
     * PUT  /participations : Updates an existing participation.
     *
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation,
     * or with status 400 (Bad Request) if the participation is not valid,
     * or with status 500 (Internal Server Error) if the participation couldnt be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @RequestMapping(value = "/participations",
        method = RequestMethod.PUT,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Participation> updateParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to update Participation : {}", participation);
        if (participation.getId() == null) {
            return createParticipation(participation);
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert("participation", participation.getId().toString()))
            .body(result);
    }

    /**
     * GET  /participations : get all the participations.
     *
     * @return the ResponseEntity with status 200 (OK) and the list of participations in body
     */
    @RequestMapping(value = "/participations",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public List<Participation> getAllParticipations() {
        log.debug("REST request to get all Participations");
        return participationService.findAll();
    }

    /**
     * GET  /participations/:id : get the "id" participation.
     *
     * @param id the id of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/participations/{id}",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Participation> getParticipation(@PathVariable Long id) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);
        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participation: get the user's participation for the "id" exercise.
     *
     * @param courseId only included for API consistency, not actually used.
     * @param exerciseId the id of the exercise for which to retrieve the participation
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participation",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Participation> getParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndCurrentUser(exerciseId);
        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    /**
     * DELETE  /participations/:id : delete the "id" participation.
     *
     * @param id the id of the participation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/participations/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long id) {
        log.debug("REST request to delete Participation : {}", id);
        participationService.delete(id);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("participation", id.toString())).build();
    }

}
