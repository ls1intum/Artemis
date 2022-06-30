package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.SHORT_NAME_PATTERN;
import static de.tum.in.www1.artemis.web.rest.util.StringUtil.stripIllegalCharacters;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TeamImportStrategyType;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.service.SubmissionService;
import de.tum.in.www1.artemis.service.TeamService;
import de.tum.in.www1.artemis.service.dto.TeamSearchUserDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import de.tum.in.www1.artemis.web.websocket.team.TeamWebsocketService;

/**
 * REST controller for managing Teams.
 */
@RestController
@RequestMapping("/api")
public class TeamResource {

    private final Logger log = LoggerFactory.getLogger(TeamResource.class);

    public static final String ENTITY_NAME = "team";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TeamRepository teamRepository;

    private final TeamService teamService;

    private final TeamWebsocketService teamWebsocketService;

    private final CourseRepository courseRepository;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationService participationService;

    private final SubmissionService submissionService;

    private final AuditEventRepository auditEventRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final TeamScoreRepository teamScoreRepository;

    public TeamResource(TeamRepository teamRepository, TeamService teamService, TeamWebsocketService teamWebsocketService, CourseRepository courseRepository,
            ExerciseRepository exerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService, ParticipationService participationService,
            SubmissionService submissionService, AuditEventRepository auditEventRepository, StudentParticipationRepository studentParticipationRepository,
            TeamScoreRepository teamScoreRepository) {
        this.teamRepository = teamRepository;
        this.teamService = teamService;
        this.teamWebsocketService = teamWebsocketService;
        this.courseRepository = courseRepository;
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.participationService = participationService;
        this.submissionService = submissionService;
        this.auditEventRepository = auditEventRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.teamScoreRepository = teamScoreRepository;
    }

    /**
     * POST /exercises/{exerciseId}/teams : Create a new team for an exercise.
     *
     * @param team       the team to create
     * @param exerciseId the exercise id for which to create a team
     * @return the ResponseEntity with status 201 (Created) and with body the new team, or with status 400 (Bad Request) if the team already has an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/exercises/{exerciseId}/teams")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Team> createTeam(@RequestBody Team team, @PathVariable long exerciseId) throws URISyntaxException {
        log.debug("REST request to save Team : {}", team);
        if (team.getId() != null) {
            throw new BadRequestAlertException("A new team cannot already have an ID", ENTITY_NAME, "idexists");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        if (!exercise.isTeamMode()) {
            throw new BadRequestAlertException("A team cannot be created for an exercise that is not team-based.", ENTITY_NAME, "exerciseNotTeamBased");
        }
        if (teamRepository.existsByExerciseCourseIdAndShortName(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), team.getShortName())) {
            throw new BadRequestAlertException("A team with this short name already exists in the course.", ENTITY_NAME, "teamShortNameAlreadyExistsInCourse");
        }
        // Remove all special characters and check if the resulting shortname is valid
        var shortName = team.getShortName().replaceAll("[^0-9a-z]", "").toLowerCase();
        Matcher shortNameMatcher = SHORT_NAME_PATTERN.matcher(shortName);
        if (!shortNameMatcher.matches()) {
            throw new BadRequestAlertException("The team name must start with a letter.", ENTITY_NAME, "teamShortNameInvalid");
        }
        // Also remove illegal characters from the long name
        team.setName(stripIllegalCharacters(team.getName()));
        // Tutors can only create teams for themselves while instructors can select any tutor as the team owner
        if (!authCheckService.isAtLeastInstructorForExercise(exercise, user)) {
            team.setOwner(user);
        }
        Team savedTeam = teamRepository.save(exercise, team);
        savedTeam.filterSensitiveInformation();
        savedTeam.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber()));
        teamWebsocketService.sendTeamAssignmentUpdate(exercise, null, savedTeam);
        return ResponseEntity.created(new URI("/api/teams/" + savedTeam.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, savedTeam.getId().toString())).body(savedTeam);
    }

    /**
     * PUT /exercises/:exerciseId/teams/:id : Updates an existing team.
     *
     * @param team       the team to update
     * @param exerciseId the id of the exercise that the team belongs to
     * @param teamId     the id of the team which to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated team, or with status 400 (Bad Request) if the team is not valid, or with status 500 (Internal
     * Server Error) if the team couldn't be updated
     */
    @PutMapping("/exercises/{exerciseId}/teams/{teamId}")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Team> updateTeam(@RequestBody Team team, @PathVariable long exerciseId, @PathVariable long teamId) {
        log.debug("REST request to update Team : {}", team);
        if (team.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idnull");
        }
        if (!team.getId().equals(teamId)) {
            throw new BadRequestAlertException("The team has an incorrect id.", ENTITY_NAME, "wrongId");
        }
        Optional<Team> existingTeam = teamRepository.findById(teamId);
        if (existingTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!existingTeam.get().getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        if (!team.getShortName().equals(existingTeam.get().getShortName())) {
            throw new BadRequestAlertException("The team's short name cannot be changed after the team has been created.", ENTITY_NAME, "shortNameChangeNotAllowed");
        }
        // Remove illegal characters from the long name
        team.setName(stripIllegalCharacters(team.getName()));

        // Prepare auth checks
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        final boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        final boolean isAtLeastTeachingAssistantAndOwner = authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)
                && authCheckService.isOwnerOfTeam(existingTeam.get(), user);

        // User must be (1) at least instructor or (2) TA but the owner of the team
        if (!isAtLeastInstructor && !isAtLeastTeachingAssistantAndOwner) {
            throw new AccessForbiddenException();
        }

        // The team owner can only be changed by instructors
        final boolean ownerWasChanged = !Objects.equals(existingTeam.get().getOwner(), team.getOwner());
        if (!isAtLeastInstructor && ownerWasChanged) {
            throw new AccessForbiddenException();
        }

        // Save team (includes check for conflicts that no student is assigned to multiple teams for an exercise)
        Team savedTeam = teamRepository.save(exercise, team);

        // Propagate team owner change to other instances of this team in the course
        if (ownerWasChanged) {
            List<Team> teamInstances = teamRepository.findAllByExerciseCourseIdAndShortName(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), savedTeam.getShortName());
            teamInstances.forEach(teamInstance -> teamInstance.setOwner(savedTeam.getOwner()));
            teamRepository.saveAll(teamInstances);
        }

        // For programming exercise teams with existing participation, the repository access needs to be updated according to the new team member set
        if (exercise instanceof ProgrammingExercise) {
            teamService.updateRepositoryMembersIfNeeded(exerciseId, existingTeam.get(), savedTeam);
        }

        savedTeam.filterSensitiveInformation();
        savedTeam.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber()));
        var participationsOfSavedTeam = studentParticipationRepository.findByExerciseIdAndTeamIdWithEagerResultsAndLegalSubmissions(exercise.getId(), savedTeam.getId());
        teamWebsocketService.sendTeamAssignmentUpdate(exercise, existingTeam.get(), savedTeam, participationsOfSavedTeam);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, team.getId().toString())).body(savedTeam);
    }

    /**
     * GET /exercises/:exerciseId/teams/:id : get the "id" team.
     *
     * @param exerciseId the id of the exercise that the team belongs to
     * @param teamId         the id of the team to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the team, or with status 404 (Not Found)
     */
    @GetMapping("/exercises/{exerciseId}/teams/{teamId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Team> getTeam(@PathVariable long exerciseId, @PathVariable long teamId) {
        log.debug("REST request to get Team : {}", teamId);
        Optional<Team> optionalTeam = teamRepository.findOneWithEagerStudents(teamId);
        if (optionalTeam.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Team team = optionalTeam.get();
        if (team.getExercise() != null && !team.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user) && !team.hasStudent(user)) {
            throw new AccessForbiddenException();
        }
        team.filterSensitiveInformation();
        return ResponseEntity.ok().body(team);
    }

    /**
     * GET /exercises/:exerciseId/teams : get all the teams of an exercise for the exercise administration page
     *
     * @param exerciseId  the exerciseId of the exercise for which all teams should be returned
     * @param teamOwnerId the user id of the team owner for which to filter the teams by (optional)
     * @return the ResponseEntity with status 200 (OK) and the list of teams in body
     */
    @GetMapping("/exercises/{exerciseId}/teams")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<Team>> getTeamsForExercise(@PathVariable long exerciseId, @RequestParam(value = "teamOwnerId", required = false) Long teamOwnerId) {
        log.debug("REST request to get all Teams for the exercise with id : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        List<Team> teams = teamRepository.findAllByExerciseIdWithEagerStudents(exercise, teamOwnerId);
        teams.forEach(Team::filterSensitiveInformation);
        teams.forEach(team -> team.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber())));
        return ResponseEntity.ok().body(teams);
    }

    /**
     * DELETE /exercises/:exerciseId/teams/:id : delete the "id" team.
     *
     * @param exerciseId the id of the exercise that the team belongs to
     * @param teamId     the id of the team to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/exercises/{exerciseId}/teams/{teamId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteTeam(@PathVariable long exerciseId, @PathVariable long teamId) {
        log.info("REST request to delete Team with id {} in exercise with id {}", teamId, exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Team team = teamRepository.findByIdElseThrow(teamId);
        if (team.getExercise() != null && !team.getExercise().getId().equals(exerciseId)) {
            throw new BadRequestAlertException("The team does not belong to the specified exercise id.", ENTITY_NAME, "wrongExerciseId");
        }
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, user);
        // Create audit event for team delete action
        var logMessage = "Delete Team with id " + teamId + " in exercise with id " + exerciseId;
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_TEAM, logMessage);
        auditEventRepository.add(auditEvent);
        // Delete all participations of the team first and then the team itself
        participationService.deleteAllByTeamId(teamId, false, false);
        // delete all team scores associated with the team
        teamScoreRepository.deleteAllByTeam(team);

        teamRepository.delete(team);

        teamWebsocketService.sendTeamAssignmentUpdate(exercise, team, null);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, Long.toString(teamId))).build();
    }

    /**
     * GET /courses/{courseId}/teams/exists?shortName={shortName} : get boolean flag whether team with shortName exists for course
     *
     * @param courseId  the id of the course for which to check teams
     * @param shortName the shortName of the team to check for existence
     * @return Response with status 200 (OK) and boolean flag in the body
     */
    @GetMapping("/courses/{courseId}/teams/exists")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<Boolean> existsTeamByShortName(@PathVariable long courseId, @RequestParam("shortName") String shortName) {
        log.debug("REST request to check Team existence for course with id {} for shortName : {}", courseId, shortName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        return ResponseEntity.ok().body(teamRepository.existsByExerciseCourseIdAndShortName(courseId, shortName));
    }

    /**
     * GET /courses/:courseId/exercises/:exerciseId/team-search-users : get all users for a given course.
     *
     * @param courseId    the id of the course for which to search users
     * @param exerciseId  the id of the exercise for which to search users to join a team
     * @param loginOrName the login or name by which to search users
     * @return the ResponseEntity with status 200 (OK) and with body all users
     */
    @GetMapping("/courses/{courseId}/exercises/{exerciseId}/team-search-users")
    @PreAuthorize("hasRole('TA')")
    public ResponseEntity<List<TeamSearchUserDTO>> searchUsersInCourse(@PathVariable long courseId, @PathVariable long exerciseId,
            @RequestParam("loginOrName") String loginOrName) {
        log.debug("REST request to search Users for {} in course with id : {}", loginOrName, courseId);
        // restrict result size by only allowing reasonable searches
        if (loginOrName.length() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query param 'loginOrName' must be three characters or longer.");
        }
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        return ResponseEntity.ok().body(teamService.searchByLoginOrNameInCourseForExerciseTeam(course, exercise, loginOrName));
    }

    /**
     * PUT /exercises/:destinationExerciseId/teams/import-from-file : add given teams into exercise
     *
     * @param exerciseId         the exercise id of the exercise for which to import teams
     * @param teams              teams whose students have login or registration number as identifiers
     * @param importStrategyType the import strategy to use when importing the teams
     * @return the ResponseEntity with status 200 (OK) and the list of created teams in body
     */
    @PutMapping("/exercises/{exerciseId}/teams/import-from-list")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<Team>> importTeamsFromList(@PathVariable long exerciseId, @RequestBody List<Team> teams, @RequestParam TeamImportStrategyType importStrategyType) {
        log.debug("REST request import given teams into destination exercise with id {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, exercise, user);

        if (!exercise.isTeamMode()) {
            throw new BadRequestAlertException("The exercise must be a team-based exercise.", ENTITY_NAME, "destinationExerciseNotTeamBased");
        }

        List<Team> filledTeams = teamService.convertTeamsStudentsToUsersInDatabase(exercise.getCourseViaExerciseGroupOrCourseMember(), teams);

        // Create audit event for team import action
        var logMessage = "Import teams from team list into exercise '" + exercise.getTitle() + "' (id: " + exercise.getId() + ") ";
        var auditEvent = new AuditEvent(user.getLogin(), Constants.IMPORT_TEAMS, logMessage);
        auditEventRepository.add(auditEvent);

        // Import teams and return the teams that now belong to the destination exercise
        List<Team> destinationTeams = teamService.importTeamsFromTeamListIntoExerciseUsingStrategy(exercise, filledTeams, importStrategyType);
        destinationTeams.forEach(Team::filterSensitiveInformation);
        destinationTeams.forEach(team -> team.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber())));

        // Send out team assignment update via websockets
        sendTeamAssignmentUpdates(exercise, destinationTeams);

        return ResponseEntity.ok().body(destinationTeams);
    }

    /**
     * PUT /exercises/:destinationExerciseId/teams/import-from-exercise/:sourceExerciseId : copy teams from source exercise into destination exercise
     *
     * @param destinationExerciseId the exercise id of the exercise for which to import teams (= destination exercise)
     * @param sourceExerciseId      the exercise id of the exercise from which to copy the teams (= source exercise)
     * @param importStrategyType    the import strategy to use when importing the teams
     * @return the ResponseEntity with status 200 (OK) and the list of created teams in body
     */
    @PutMapping("/exercises/{destinationExerciseId}/teams/import-from-exercise/{sourceExerciseId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<List<Team>> importTeamsFromSourceExercise(@PathVariable long destinationExerciseId, @PathVariable long sourceExerciseId,
            @RequestParam TeamImportStrategyType importStrategyType) {
        log.debug("REST request import all teams from source exercise with id {} into destination exercise with id {}", sourceExerciseId, destinationExerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise destinationExercise = exerciseRepository.findByIdElseThrow(destinationExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, destinationExercise, user);

        if (destinationExerciseId == sourceExerciseId) {
            throw new BadRequestAlertException("The source and destination exercise must be different.", ENTITY_NAME, "sourceDestinationExerciseNotDifferent");
        }
        if (!destinationExercise.isTeamMode()) {
            throw new BadRequestAlertException("The destination exercise must be a team-based exercise.", ENTITY_NAME, "destinationExerciseNotTeamBased");
        }
        Exercise sourceExercise = exerciseRepository.findByIdElseThrow(sourceExerciseId);
        if (!sourceExercise.isTeamMode()) {
            throw new BadRequestAlertException("The source exercise must be a team-based exercise.", ENTITY_NAME, "sourceExerciseNotTeamBased");
        }

        // Create audit event for team import action
        var logMessage = "Import teams from source exercise '" + sourceExercise.getTitle() + "' (id: " + sourceExercise.getId() + ") into destination exercise '"
                + destinationExercise.getTitle() + "' (id: " + destinationExercise.getId() + ") using strategy " + importStrategyType;
        var auditEvent = new AuditEvent(user.getLogin(), Constants.IMPORT_TEAMS, logMessage);
        auditEventRepository.add(auditEvent);

        // Import teams and return the teams that now belong to the destination exercise
        List<Team> destinationTeams = teamService.importTeamsFromSourceExerciseIntoDestinationExerciseUsingStrategy(sourceExercise, destinationExercise, importStrategyType);
        destinationTeams.forEach(Team::filterSensitiveInformation);
        destinationTeams.forEach(team -> team.getStudents().forEach(student -> student.setVisibleRegistrationNumber(student.getRegistrationNumber())));
        // Send out team assignment update via websockets
        sendTeamAssignmentUpdates(destinationExercise, destinationTeams);

        return ResponseEntity.ok().body(destinationTeams);
    }

    /**
     * GET /courses/:courseId/teams/:teamShortName/with-exercises-and-participations : get course "id" with all released exercises in which the team "teamShortName" exists
     * together with its participations for those exercises (and the latest submission for those if the user is an instructor or the team tutor)
     *
     * @param courseId      id of the course
     * @param teamShortName short name of the team (all teams with the short name in the course are seen as the same team)
     * @return Course with exercises and participations (and latest submissions) for the team
     */
    @GetMapping(value = "/courses/{courseId}/teams/{teamShortName}/with-exercises-and-participations")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Course> getCourseWithExercisesAndParticipationsForTeam(@PathVariable Long courseId, @PathVariable String teamShortName) {
        log.debug("REST request to get Course {} with exercises and participations for Team with short name {}", courseId, teamShortName);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        if (!(authCheckService.isAtLeastTeachingAssistantInCourse(course, user) || authCheckService.isStudentInTeam(course, teamShortName, user))) {
            throw new AccessForbiddenException();
        }

        // Get all team instances in course with the given team short name
        Set<Exercise> exercises = exerciseRepository.findAllTeamExercisesByCourseId(course.getId());
        List<Team> teams = teamRepository.findAllByExerciseCourseIdAndShortName(course.getId(), teamShortName);
        Map<Long, Team> exerciseTeamMap = teams.stream().collect(Collectors.toMap(team -> team.getExercise().getId(), team -> team));

        // Filter course exercises by: team needs to exist for exercise
        exercises = exercises.stream().filter(exercise -> exerciseTeamMap.containsKey(exercise.getId())).collect(Collectors.toSet());

        // For students: Filter course exercises by their visibility to students
        if (!authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            exercises = exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toSet());
        }

        // Set teams on exercises
        exercises.forEach(exercise -> exercise.setTeams(Set.of(exerciseTeamMap.get(exercise.getId()))));

        // Fetch participations for team
        List<StudentParticipation> participations;
        if (authCheckService.isAtLeastInstructorInCourse(course, user) || teams.stream().map(Team::getOwner).allMatch(user::equals)) {
            // fetch including submissions and results for team tutor and instructors
            participations = studentParticipationRepository.findAllByCourseIdAndTeamShortNameWithEagerLegalSubmissionsResult(course.getId(), teamShortName);
            submissionService.reduceParticipationSubmissionsToLatest(participations, false);
        }
        else {
            // for other tutors and for students: submissions not needed, hide results
            participations = studentParticipationRepository.findAllByCourseIdAndTeamShortName(course.getId(), teamShortName);
            participations.forEach(participation -> participation.setResults(null));
        }

        // Set the submission count for all participations
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countLegalSubmissionsPerParticipationByCourseIdAndTeamShortNameAsMap(courseId, teamShortName);
        participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));

        // Set studentParticipations on all exercises
        List<StudentParticipation> finalParticipations = participations;
        exercises.forEach(exercise -> {
            Optional<StudentParticipation> studentParticipation = finalParticipations.stream().filter(participation -> participation.getExercise().equals(exercise)).findAny();
            studentParticipation.ifPresent(participation -> exercise.setStudentParticipations(Set.of(participation)));
        });

        // Filter sensitive information
        exercises.forEach(Exercise::filterSensitiveInformation);

        course.setExercises(exercises);
        return ResponseEntity.ok(course);
    }

    /**
     * Sends students in each team an update about their assignments to the teams
     * Along with participation if they have any
     *
     * @param exercise Exercise which students will receive team update
     * @param teams    Teams of exercise
     */
    private void sendTeamAssignmentUpdates(Exercise exercise, List<Team> teams) {
        // Get participation to given exercise into a map which participation identifiers as key and a lists of all participation with that identifier as value
        Map<String, List<StudentParticipation>> participationsMap = studentParticipationRepository.findByExerciseIdWithEagerLegalSubmissionsResult(exercise.getId()).stream()
                .collect(Collectors.toMap(StudentParticipation::getParticipantIdentifier, List::of, (a, b) -> Stream.concat(a.stream(), b.stream()).toList()));

        // Send out team assignment update via websockets to each team
        teams.forEach(team -> teamWebsocketService.sendTeamAssignmentUpdate(exercise, null, team, participationsMap.getOrDefault(team.getParticipantIdentifier(), List.of())));
    }
}
