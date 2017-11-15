package de.tum.in.www1.exerciseapp.web.rest;

import com.codahale.metrics.annotation.Timed;
import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.ResultRepository;
import de.tum.in.www1.exerciseapp.security.AuthoritiesConstants;
import de.tum.in.www1.exerciseapp.service.ContinuousIntegrationService;
import de.tum.in.www1.exerciseapp.service.ExerciseService;
import de.tum.in.www1.exerciseapp.service.ParticipationService;
import de.tum.in.www1.exerciseapp.service.VersionControlService;
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
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.Principal;
import java.time.ZonedDateTime;
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

    private final ParticipationService participationService;

    private final ParticipationRepository participationRepository;

    private final ResultRepository resultRepository;

    private final ExerciseService exerciseService;

    private final Optional<ContinuousIntegrationService> continuousIntegrationService;

    private final Optional<VersionControlService> versionControlService;

    private static final String ENTITY_NAME = "participation";

    private final GrantedAuthority adminAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN);
    private final GrantedAuthority taAuthority = new SimpleGrantedAuthority(AuthoritiesConstants.TEACHING_ASSISTANT);

    public ParticipationResource(ParticipationService participationService, ParticipationRepository participationRepository, ResultRepository resultRepository, ExerciseService exerciseService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService) {
        this.participationService = participationService;
        this.participationRepository = participationRepository;
        this.resultRepository = resultRepository;
        this.exerciseService = exerciseService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
    }

    /**
     * POST  /participations : Create a new participation.
     *
     * @param participation the participation to create
     * @return the ResponseEntity with status 201 (Created) and with body the new participation, or with status 400 (Bad Request) if the participation has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/participations")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> createParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to save Participation : {}", participation);
        if (participation.getId() != null) {
            throw new BadRequestAlertException("A new participation cannot already have an ID", ENTITY_NAME, "idexists");
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.created(new URI("/api/participations/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(ENTITY_NAME, result.getId().toString()))
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
    @PostMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participations")
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
     * POST  /courses/:courseId/exercises/:exerciseId/resume-participation: resume the participation of the current user in the exercise identified by id
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId id of the exercise for which to resume participation
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK) and with updated participation as a body, or with status 500 (Internal Server Error)
     */
    @PutMapping(value = "/courses/{courseId}/exercises/{exerciseId}/resume-participation")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> resumeParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) throws URISyntaxException {
        log.debug("REST request to resume Exercise : {}", exerciseId);
        Exercise exercise = exerciseService.findOne(exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
        if (exercise instanceof ProgrammingExercise) {
            participation = participationService.resume(exercise, participation);
            return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, participation.getId().toString()))
                .body(participation);
        }
        log.debug("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to resume participation", exerciseId);
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT  /participations : Updates an existing participation.
     *
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation,
     * or with status 400 (Bad Request) if the participation is not valid,
     * or with status 500 (Internal Server Error) if the participation couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/participations")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> updateParticipation(@RequestBody Participation participation) throws URISyntaxException {
        log.debug("REST request to update Participation : {}", participation);
        if (participation.getId() == null) {
            return createParticipation(participation);
        }
        Participation result = participationService.save(participation);
        return ResponseEntity.ok()
            .headers(HeaderUtil.createEntityUpdateAlert(ENTITY_NAME, participation.getId().toString()))
            .body(result);
    }

    //Deactivated because it would load all (thousands) participations and completely overload the server
//    /**
//     * GET  /participations : get all the participations.
//     *
//     * @return the ResponseEntity with status 200 (OK) and the list of participations in body
//     */
//    @GetMapping("/participations")
//    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
//    @Timed
//    public List<Participation> getAllParticipations() {
//        log.debug("REST request to get all Participations");
//        return participationService.findAll();
//    }

    /**
     * GET  /exercise/{exerciseId}/participations : get all the participations for an exercise
     *
     * @param exerciseId
     * @return
     */
    @GetMapping(value = "/exercise/{exerciseId}/participations")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public List<Participation> getAllParticipationsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        return participationRepository.findByExerciseId(exerciseId);
    }

    /**
     * GET  /course/{courseId}/participations : get all the participations for a course
     *
     * @param courseId
     * @return
     */
    @GetMapping(value = "/courses/{courseId}/participations")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public List<Participation> getAllParticipationsForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Participations for Course {}", courseId);
        return participationRepository.findByCourseId(courseId);
    }

    /**
     * GET  /participations/:id : get the "id" participation.
     *
     * @param id the id of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("/participations/{id}")
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

    @GetMapping(value = "/participations/{id}/repositoryWebUrl")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity<String> getParticipationRepositoryWebUrl(@PathVariable Long id, Authentication authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);

        AbstractAuthenticationToken user = (AbstractAuthenticationToken) authentication;
        if (participation != null && (!participation.getStudent().getLogin().equals(user.getName()) && !(user.getAuthorities().contains(adminAuthority) && !user.getAuthorities().contains(taAuthority)))) {
            //return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        URL url = versionControlService.get().getRepositoryWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @GetMapping(value = "/participations/{id}/buildPlanWebUrl")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity<String> getParticipationBuildPlanWebUrl(@PathVariable Long id, Authentication authentication) {
        log.debug("REST request to get Participation : {}", id);
        Participation participation = participationService.findOne(id);

        AbstractAuthenticationToken user = (AbstractAuthenticationToken) authentication;
        if (participation != null && (!participation.getStudent().getLogin().equals(user.getName()) && !(user.getAuthorities().contains(adminAuthority) && !user.getAuthorities().contains(taAuthority)))) {
            //return new ResponseEntity<>(HttpStatus.FORBIDDEN);
        }

        URL url = continuousIntegrationService.get().getBuildPlanWebUrl(participation);
        return Optional.ofNullable(url)
            .map(result -> new ResponseEntity<>(
                url.toString(),
                HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }


    @GetMapping(value = "/participations/{id}/buildArtifact")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    public ResponseEntity getParticipationBuildArtifact(@PathVariable Long id, Authentication authentication) {
        log.debug("REST request to get Participation build artifact: {}", id);
        Participation participation = participationService.findOne(id);
        return continuousIntegrationService.get().retrieveLatestArtifact(participation);
    }


    /**
     * GET  /courses/:courseId/exercises/:exerciseId/participation: get the user's participation for the "id" exercise.
     *
     * @param courseId   only included for API consistency, not actually used
     * @param exerciseId the id of the exercise for which to retrieve the participation
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping(value = "/courses/{courseId}/exercises/{exerciseId}/participation")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Participation> getParticipation(@PathVariable Long courseId, @PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Participation participation = participationService.findOneByExerciseIdAndStudentLogin(exerciseId, principal.getName());
        return Optional.ofNullable(participation)
            .map(result -> new ResponseEntity<>(result, HttpStatus.OK))
            .orElse(new ResponseEntity<>(HttpStatus.NO_CONTENT));
    }

    /**
     * GET  /participations/:id/status: get build status of the user's participation for the "id" participation.
     *
     * @param id the participation id
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping(value = "/participations/{id}/status")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'ADMIN')")
    @Timed
    public ResponseEntity<?> getParticipationStatus(@PathVariable Long id) {
        Participation participation = participationService.findOne(id);
        if (participation.getExercise() instanceof QuizExercise) {
            QuizExercise.Status status = QuizExercise.statusForQuiz((QuizExercise) participation.getExercise());
            return new ResponseEntity<>(status, HttpStatus.OK);
        } else if (participation.getExercise() instanceof ProgrammingExercise) {
            ContinuousIntegrationService.BuildStatus buildStatus = continuousIntegrationService.get().getBuildStatus(participation);
            return Optional.ofNullable(buildStatus)
                .map(status -> new ResponseEntity<>(
                    status,
                    HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
        }
        return ResponseEntity.unprocessableEntity().build();
    }

    /**
     * DELETE  /participations/:id : delete the "id" participation.
     *
     * @param id the id of the participation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/participations/{id}")
    @PreAuthorize("hasAnyRole('TA', 'ADMIN')")
    @Timed
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long id,
                                                    @RequestParam(defaultValue = "false") boolean deleteBuildPlan,
                                                    @RequestParam(defaultValue = "false") boolean deleteRepository) {
        log.debug("REST request to delete Participation : {}, deleteBuildPlan: {}, deleteRepository: {}", id, deleteBuildPlan, deleteRepository);
        participationService.delete(id, deleteBuildPlan, deleteRepository);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert("participation", id.toString())).build();
    }
}
