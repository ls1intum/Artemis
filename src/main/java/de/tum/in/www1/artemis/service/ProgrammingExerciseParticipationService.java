package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseParticipationService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

    private final ParticipationService participationService;

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final ParticipationRepository participationRepository;

    private final TeamRepository teamRepository;

    private final Optional<VersionControlService> versionControlService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    private final GitService gitService;

    public ProgrammingExerciseParticipationService(ParticipationService participationService, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ParticipationRepository participationRepository, TeamRepository teamRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, Optional<VersionControlService> versionControlService, UserService userService,
            AuthorizationCheckService authCheckService, GitService gitService) {
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.versionControlService = versionControlService;
        this.authCheckService = authCheckService;
        this.userService = userService;
        this.gitService = gitService;
    }

    public Participation findParticipation(Long participationId) throws EntityNotFoundException {
        return participationService.findOne(participationId);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipation(Long participationId) {
        return studentParticipationRepository.findById(participationId);
    }

    public Optional<TemplateProgrammingExerciseParticipation> findTemplateParticipation(Long participationId) {
        return templateParticipationRepository.findById(participationId);
    }

    public Optional<SolutionProgrammingExerciseParticipation> findSolutionParticipation(Long participationId) {
        return solutionParticipationRepository.findById(participationId);
    }

    /**
     * Retrieve the solution participation of the given programming exercise.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @return the SolutionProgrammingExerciseParticipation of programming exercise.
     * @throws EntityNotFoundException if the SolutionParticipation can't be found (could be that the programming exercise does not exist or it does not have a SolutionParticipation).
     */
    public SolutionProgrammingExerciseParticipation findSolutionParticipationByProgrammingExerciseId(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<SolutionProgrammingExerciseParticipation> solutionParticipation = solutionParticipationRepository.findByProgrammingExerciseId(programmingExerciseId);
        if (solutionParticipation.isEmpty()) {
            throw new EntityNotFoundException("Could not find solution participation for programming exercise with id " + programmingExerciseId);
        }
        return solutionParticipation.get();
    }

    /**
     * Retrieve the template participation of the given programming exercise.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @return the TemplateProgrammingExerciseParticipation of programming exercise.
     * @throws EntityNotFoundException if the TemplateParticipation can't be found (could be that the programming exercise does not exist or it does not have a TemplateParticipation).
     */
    public TemplateProgrammingExerciseParticipation findTemplateParticipationByProgrammingExerciseId(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<TemplateProgrammingExerciseParticipation> templateParticipation = templateParticipationRepository.findByProgrammingExerciseId(programmingExerciseId);
        if (templateParticipation.isEmpty()) {
            throw new EntityNotFoundException("Could not find solution participation for programming exercise with id " + programmingExerciseId);
        }
        return templateParticipation.get();
    }

    /**
     * Tries to retrieve a student participation for the given exercise id and username.
     *
     * @param exercise the exercise for which to find a participation
     * @param username of the user to which the participation belongs.
     * @return the participation for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    @NotNull
    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseAndStudentId(Exercise exercise, String username) throws EntityNotFoundException {
        Optional<ProgrammingExerciseStudentParticipation> participation;
        if (exercise.isTeamMode()) {
            Optional<Team> optionalTeam = teamRepository.findOneByExerciseIdAndUserLogin(exercise.getId(), username);
            participation = optionalTeam.flatMap(team -> studentParticipationRepository.findByExerciseIdAndTeamId(exercise.getId(), team.getId()));
        }
        else {
            participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), username);
        }
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("participation could not be found by exerciseId " + exercise.getId() + " and user " + username);
        }
        return participation.get();
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId) {
        return studentParticipationRepository.findByExerciseId(exerciseId);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findByExerciseIdAndTeamId(Long exerciseId, Long teamId) {
        return studentParticipationRepository.findByExerciseIdAndTeamId(exerciseId, teamId);
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseAndParticipationIds(Long exerciseId, Set<Long> participationIds) {
        return studentParticipationRepository.findByExerciseIdAndParticipationIds(exerciseId, participationIds);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacksAndRelatedSubmissions(Long participationId) {
        return studentParticipationRepository.findByIdWithLatestResultAndFeedbacksAndRelatedSubmissions(participationId, ZonedDateTime.now());
    }

    /**
     * Try to find a programming exercise participation for the given id.
     *
     * @param participationId ProgrammingExerciseParticipation id
     * @return the casted participation
     * @throws EntityNotFoundException if the participation with the given id does not exist or is not a programming exercise participation.
     */
    public ProgrammingExerciseParticipation findProgrammingExerciseParticipationWithLatestSubmissionAndResult(Long participationId) throws EntityNotFoundException {
        Optional<Participation> participation = participationRepository.findByIdWithLatestSubmissionAndResult(participationId);
        if (participation.isEmpty() || !(participation.get() instanceof ProgrammingExerciseParticipation)) {
            throw new EntityNotFoundException("No programming exercise participation found with id " + participationId);
        }
        return (ProgrammingExerciseParticipation) participation.get();
    }

    /**
     * Check if the user can access a given participation by accessing the exercise and course connected to this participation
     * The method will treat the participation types differently:
     * - ProgrammingExerciseStudentParticipations should only be accessible by its owner (student) and the courses instructor/tas.
     * - Template/SolutionParticipations should only be accessible by the courses instructor/tas.
     *
     * @param participation to check permissions for.
     * @return true if the user can access the participation, false if not. Also returns false if the participation is not from a programming exercise.
     */
    public boolean canAccessParticipation(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            return canAccessParticipation((ProgrammingExerciseStudentParticipation) participation);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return canAccessParticipation((SolutionProgrammingExerciseParticipation) participation);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return canAccessParticipation((TemplateProgrammingExerciseParticipation) participation);
        }
        return false;
    }

    private boolean canAccessParticipation(@NotNull ProgrammingExerciseStudentParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return participation.isOwnedBy(user) || authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    private boolean canAccessParticipation(@NotNull SolutionProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        // To prevent null pointer exceptions, we therefore retrieve it again as concrete solution programming exercise participation
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            participation = findSolutionParticipation(participation.getId()).get();
        }
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    private boolean canAccessParticipation(@NotNull TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        // To prevent null pointer exceptions, we therefore retrieve it again as concrete template programming exercise participation
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            participation = findTemplateParticipation(participation.getId()).get();
        }
        Course course = participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        return authCheckService.isAtLeastTeachingAssistantInCourse(course, user);
    }

    /**
     * Check if the user can access a given participation.
     * The method will treat the participation types differently:
     * - ProgrammingExerciseStudentParticipations should only be accessible by its owner (student) and the courses instructor/tas.
     * - Template/SolutionParticipations should only be accessible by the courses instructor/tas.
     *
     * @param participation to check permissions for.
     * @param principal object to check permissions of the user with.
     * @return true if the user can access the participation, false if not. Also returns false if the participation is not from a programming exercise.
     */
    public boolean canAccessParticipation(ProgrammingExerciseParticipation participation, Principal principal) {
        if (participation instanceof ProgrammingExerciseStudentParticipation) {
            return canAccessParticipation((ProgrammingExerciseStudentParticipation) participation, principal);
        }
        else if (participation instanceof SolutionProgrammingExerciseParticipation) {
            return canAccessParticipation((SolutionProgrammingExerciseParticipation) participation, principal);
        }
        else if (participation instanceof TemplateProgrammingExerciseParticipation) {
            return canAccessParticipation((TemplateProgrammingExerciseParticipation) participation, principal);
        }
        return false;
    }

    private boolean canAccessParticipation(ProgrammingExerciseStudentParticipation participation, Principal principal) {
        return participation.isOwnedBy(principal.getName());
    }

    private boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal.getName());
        return authCheckService.isAtLeastInstructorForExercise(participation.getExercise(), user);
    }

    private boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal.getName());
        return authCheckService.isAtLeastInstructorForExercise(participation.getExercise(), user);
    }

    /**
     * Setup the initial solution participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URL. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     */
    @NotNull
    public void setupInitialSolutionParticipation(ProgrammingExercise newExercise) {
        final String solutionRepoName = newExercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        newExercise.setSolutionParticipation(solutionParticipation);
        solutionParticipation.setBuildPlanId(newExercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(newExercise.getProjectKey(), solutionRepoName).toString());
        solutionParticipation.setProgrammingExercise(newExercise);
        solutionParticipationRepository.save(solutionParticipation);
    }

    /**
     * Setup the initial template participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URL. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     */
    @NotNull
    public void setupInitalTemplateParticipation(ProgrammingExercise newExercise) {
        final String exerciseRepoName = newExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setBuildPlanId(newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(newExercise.getProjectKey(), exerciseRepoName).toString());
        templateParticipation.setProgrammingExercise(newExercise);
        newExercise.setTemplateParticipation(templateParticipation);
        templateParticipationRepository.save(templateParticipation);
    }

    /**
     * Lock the repository associated with a programming participation
     *
     * @param programmingExercise the programming exercise
     * @param participation the programming exercise student participation whose repository should be locked
     * @throws VersionControlException if locking was not successful, e.g. if the repository was already locked
     */
    public void lockStudentRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.get().setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), participation.getStudents());
        }
        else {
            log.warn("Cannot lock student repository for participation " + participation.getId() + " because the repository was not copied yet!");
        }
    }

    /**
     * Unlock the repository associated with a programming participation
     *
     * @param programmingExercise the programming exercise
     * @param participation the programming exercise student participation whose repository should be unlocked
     * @throws VersionControlException if unlocking was not successful, e.g. if the repository was already unlocked
     */
    public void unlockStudentRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.get().configureRepository(programmingExercise, participation.getVcsRepositoryUrl(), participation.getStudents(), true);
        }
        else {
            log.warn("Cannot unlock student repository for participation " + participation.getId() + " because the repository was not copied yet!");
        }
    }

    /**
     * Stashes all changes, which were not submitted/committed before the due date, of a programming participation
     *
     * @param programmingExercise exercise with information about the due date
     * @param participation student participation whose not submitted changes will be stashed
     */
    public void stashChangesInStudentRepositoryAfterDueDateHasPassed(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            try {
                // Note: exam exercise do not have a due date, this method should only be invoked directly after the due date so now check is needed here
                Repository repo = gitService.getOrCheckoutRepository(participation);
                gitService.stashChanges(repo);
            }
            catch (InterruptedException | GitAPIException e) {
                log.error("Stashing student repository for participation " + participation.getId() + " in exercise '" + programmingExercise.getTitle()
                        + "' did not work as expected: " + e.getMessage());
            }
        }
        else {
            log.warn("Cannot stash student repository for participation " + participation.getId() + " because the repository was not copied yet!");
        }
    }
}
