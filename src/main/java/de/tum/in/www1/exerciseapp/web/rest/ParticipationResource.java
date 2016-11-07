package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.service.ContinuousIntegrationService;
import de.tum.in.www1.exerciseapp.service.ExerciseService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.service.VersionControlService;
import de.tum.in.www1.exerciseapp.web.rest.util.HeaderUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import javax.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for managing Participation.
 */
@RestController
@RequestMapping({"/api", "/api_basic"})
@PreAuthorize("hasRole('ADMIN')")
public class ParticipationResource {

    private final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    @Inject
    private ParticipationService participationService;

    @Inject
    private ExerciseService exerciseService;

    @Inject
    private ContinuousIntegrationService continuousIntegrationService;

    @Inject
    private VersionControlService versionControlService;


    private GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
    private GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);

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
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to init a participation
     * @param principal  the current user principal
     * @return the ResponseEntity with status 200 (OK) and with body the exercise, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations",
        method = RequestMethod.POST,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> initParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) throws URISyntaxException {
        log.debug("REST request to init Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (Optional.ofNullable(exercise).isPresent()) {
            Participation participation = participationService.init(exercise, principal.getName());
            return ResponseEntity.created(new URI("/api/participations/" + participation.getId()))
                .body(participation);
        } else {
            return ResponseEntity.badRequest().headers(HeaderUtil.createFailureAlert("participation", "exerciseNotFound", "No exercise was found for the given ID")).body(null);
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
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity<Participation> getParticipation(@PathVariable Long id, AbstractAuthenticationToken authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (participation != null && (!participation.getStudent().getLogin().equals(authentication.getName()) && !(authentication.getAuthorities().contains(adminAuthority) && !authentication.getAuthorities().contains(taAuthority)))) {
            //return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @RequestMapping(value = "/participations/{id}/repositoryWebUrl",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity<String> getParticipationRepositoryWebUrl(@PathVariable Long id, AbstractAuthenticationToken authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (participation != null && (!participation.getStudent().getLogin().equals(authentication.getName()) && !(authentication.getAuthorities().contains(adminAuthority) && !authentication.getAuthorities().contains(taAuthority)))) {
            //return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        URL url = versionControlService.getRepositoryWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @RequestMapping(value = "/participations/{id}/buildPlanWebUrl",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity<String> getParticipationBuildPlanWebUrl(@PathVariable Long id, AbstractAuthenticationToken authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);

        if (participation != null && (!participation.getStudent().getLogin().equals(authentication.getName()) && !(authentication.getAuthorities().contains(adminAuthority) && !authentication.getAuthorities().contains(taAuthority)))) {
            //return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        URL url = continuousIntegrationService.getBuildPlanWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participation: get the user's participation for the "id" exercise.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the participation
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participation",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> getParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(
                result,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participation/status: get build status of the user's participation for the "id" exercise.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the participation status
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @RequestMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participation/status",
        method = RequestMethod.GET,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<?> getParticipationStatus(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation status for Exercise : {}", exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
        ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.getBuildStatus(participation);
        return Optional.ofNullable(buildStatus)
            .map(status -> new ResponseEntity<>(
                status,
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    /**
     * DELETE  /participations/:id : delete the "id" participation.
     *
     * @param id               the id of the participation to delete
     * @param deleteBuildPlan  true if the associated build plan should be deleted
     * @param deleteRepository true if the associated repository should be deleted
     * @return the ResponseEntity with status 200 (OK)
     */
    @RequestMapping(value = "/participations/{id}",
        method = RequestMethod.DELETE,
        produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "false") boolean deleteBuildPlan,
                                                    @RequestParam(defaultValue = "false") boolean deleteRepository) {
        log.debug("REST request to delete Participation : {}, deleteBuildPlan: {}, deleteRepository: {}", id, deleteBuildPlan, deleteRepository);
        participationService.delete(id, deleteBuildPlan, deleteRepository);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("participation", id.toString())).build();
    }

}
