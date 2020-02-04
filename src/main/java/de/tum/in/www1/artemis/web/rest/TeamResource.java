package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Team;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.TeamRepository;
import de.tum.in.www1.artemis.service.*;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Teams.
 */
@RestController
@RequestMapping("/api")
public class TeamResource {

    private final Logger log = LoggerFactory.getLogger(TeamResource.class);

    private static final String ENTITY_NAME = "team";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TeamRepository teamRepository;

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public TeamResource(TeamRepository teamRepository, ExerciseService exerciseService, UserService userService, AuthorizationCheckService authCheckService) {
        this.teamRepository = teamRepository;
        this.exerciseService = exerciseService;
        this.userService = userService;
        this.authCheckService = authCheckService;
    }

    /**
     * POST /exercises/{exerciseId}/teams : Create a new team for an exercise.
     *
     * @param team the team to create
     * @param exerciseId the exercise id for which to create a team
     * @return the ResponseEntity with status 201 (Created) and with body the new team, or with status 400 (Bad Request) if the team already has an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/exercises/{exerciseId}/teams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> createTeam(@RequestBody Team team, @PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to save Team : {}", team);
        if (team.getId() != null) {
            throw new BadRequestAlertException("A new team cannot already have an ID", ENTITY_NAME, "idexists");
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        team.setExercise(exercise);
        Team result = teamRepository.save(team);
        return ResponseEntity.created(new URI("/api/teams/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PUT /teams : Updates an existing team.
     *
     * @param team the team to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated team, or with status 400 (Bad Request) if the team is not valid, or with status 500 (Internal
     * Server Error) if the team couldn't be updated
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PutMapping("/teams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> updateTeam(@RequestBody Team team) throws URISyntaxException {
        log.debug("REST request to update Team : {}", team);
        if (team.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        Optional<Team> existingTeam = teamRepository.findById(team.getId());
        if (existingTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(existingTeam.get().getExercise().getId());
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        team.setExercise(exercise);
        Team result = teamRepository.save(team);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, team.getId().toString())).body(result);
    }

    /**
     * GET /teams/:id : get the "id" team.
     *
     * @param id the id of the team to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the team, or with status 404 (Not Found)
     */
    @GetMapping("/teams/{id}")
    @PreAuthorize("hasAnyRole('USER', 'TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Team> getTeam(@PathVariable Long id) {
        log.debug("REST request to get Team : {}", id);
        Optional<Team> optionalTeam = teamRepository.findOneWithEagerStudents(id);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = userService.getUserWithGroupsAndAuthorities();
        Team team = optionalTeam.get();
        Exercise exercise = exerciseService.findOne(team.getExercise().getId());
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user) && !team.hasStudent(user)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(team);
    }

    /**
     * GET /exercises/:exerciseId/teams : get all the teams of an exercise for the exercise administration page
     *
     * @param exerciseId the exerciseId of the exercise for which all teams should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of teams in body
     */
    @GetMapping(value = "/exercises/{exerciseId}/teams")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Set<Team>> getTeamsForExercise(@PathVariable Long exerciseId) {
        log.debug("REST request to get all Teams for the exercise with id : {}", exerciseId);
        User user = userService.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseService.findOne(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        return ResponseEntity.ok().body(teamRepository.findAllByExerciseIdWithEagerStudents(exerciseId));
    }

    /**
     * DELETE /teams/:id : delete the "id" team.
     *
     * @param id the id of the team to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/teams/{id}")
    @PreAuthorize("hasAnyRole('TA', 'INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<Void> deleteTeam(@PathVariable Long id) {
        log.debug("REST request to delete Team : {}", id);
        User user = userService.getUserWithGroupsAndAuthorities();
        Optional<Team> optionalTeam = teamRepository.findById(id);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Team team = optionalTeam.get();
        Exercise exercise = exerciseService.findOne(team.getExercise().getId());
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
            return forbidden();
        }
        teamRepository.delete(team);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, id.toString())).build();
    }
}
