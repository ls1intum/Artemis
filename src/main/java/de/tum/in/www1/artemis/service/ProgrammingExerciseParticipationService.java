package de.tum.in.www1.artemis.service;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.constraints.NotNull;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseParticipationService {

    private final ParticipationService participationService;

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final ParticipationRepository participationRepository;

    private final Optional<VersionControlService> versionControlService;

    private final AuthorizationCheckService authCheckService;

    private final UserService userService;

    public ProgrammingExerciseParticipationService(ParticipationService participationService, SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository studentParticipationRepository, ParticipationRepository participationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, Optional<VersionControlService> versionControlService, UserService userService,
            AuthorizationCheckService authCheckService) {
        this.participationService = participationService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.versionControlService = versionControlService;
        this.authCheckService = authCheckService;
        this.userService = userService;
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
     * @param exerciseId id of the exercise.
     * @param username of the user to which the participation belongs.
     * @return the participation for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    @Transactional(readOnly = true)
    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseIdAndStudentId(Long exerciseId, String username) throws EntityNotFoundException {
        Optional<ProgrammingExerciseStudentParticipation> participation = studentParticipationRepository.findByExerciseIdAndStudentLogin(exerciseId, username);
        if (participation.isEmpty()) {
            throw new EntityNotFoundException("participation could not be found by exerciseId " + exerciseId + " and user " + username);
        }
        // Make sure the template participation is not a proxy object in this case
        Hibernate.initialize(participation.get().getProgrammingExercise().getTemplateParticipation());
        return participation.get();
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseId(Long exerciseId) {
        return studentParticipationRepository.findByExerciseId(exerciseId);
    }

    public List<ProgrammingExerciseStudentParticipation> findByExerciseAndParticipationIds(Long exerciseId, Set<Long> participationIds) {
        return studentParticipationRepository.findByExerciseIdAndParticipationIds(exerciseId, participationIds);
    }

    public Optional<ProgrammingExerciseStudentParticipation> findStudentParticipationWithLatestResultAndFeedbacks(Long participationId) {
        return studentParticipationRepository.findByIdWithLatestResultAndFeedbacks(participationId);
    }

    /**
     * Try to find a programming exercise participation for the given id.
     *
     * @param participationId ProgrammingExerciseParticipation id
     * @return the casted participation
     * @throws EntityNotFoundException if the participation with the given id does not exist or is not a programming exercise participation.
     */
    public ProgrammingExerciseParticipation findProgrammingExerciseParticipationWithLatestResultAndFeedbacks(Long participationId) throws EntityNotFoundException {
        Optional<Participation> participation = participationRepository.findByIdWithLatestResultAndFeedbacks(participationId);
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
        return participation.getStudent().getLogin().equals(user.getLogin()) || authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    private boolean canAccessParticipation(@NotNull SolutionProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        // To prevent null pointer exceptions, we therefore retrieve it again as concrete solution programming exercise participation
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            participation = findSolutionParticipation(participation.getId()).get();
        }
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getProgrammingExercise(), user);
    }

    private boolean canAccessParticipation(@NotNull TemplateProgrammingExerciseParticipation participation) {
        User user = userService.getUserWithGroupsAndAuthorities();
        // Note: if this participation was retrieved as Participation (abstract super class) from the database, the programming exercise might not be correctly initialized
        // To prevent null pointer exceptions, we therefore retrieve it again as concrete template programming exercise participation
        if (participation.getProgrammingExercise() == null || !Hibernate.isInitialized(participation.getProgrammingExercise())) {
            participation = findTemplateParticipation(participation.getId()).get();
        }
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getProgrammingExercise(), user);
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

    public boolean canAccessParticipation(ProgrammingExerciseStudentParticipation participation, Principal principal) {
        return participation.getStudent().getLogin().equals(principal.getName());
    }

    public boolean canAccessParticipation(SolutionProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    public boolean canAccessParticipation(TemplateProgrammingExerciseParticipation participation, Principal principal) {
        User user = userService.getUserWithGroupsAndAuthorities(principal);
        return authCheckService.isAtLeastTeachingAssistantForExercise(participation.getExercise(), user);
    }

    /**
     * Setup the initial solution participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URL. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     * @param projectKey The key of the project of the new exercise
     * @param solutionPlanName The name for the build plan of the participation
     */
    @NotNull
    public void setupInitialSolutionParticipation(ProgrammingExercise newExercise, String projectKey, String solutionPlanName) {
        final String solutionRepoName = projectKey.toLowerCase() + "-" + RepositoryType.SOLUTION.getName();
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        newExercise.setSolutionParticipation(solutionParticipation);
        solutionParticipation.setBuildPlanId(projectKey + "-" + solutionPlanName);
        solutionParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, solutionRepoName).toString());
        solutionParticipation.setProgrammingExercise(newExercise);
        solutionParticipationRepository.save(solutionParticipation);
    }

    /**
     * Setup the initial template participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URL. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     * @param projectKey The key of the project of the new exercise
     * @param templatePlanName The name for the build plan of the participation
     */
    @NotNull
    public void setupInitalTemplateParticipation(ProgrammingExercise newExercise, String projectKey, String templatePlanName) {
        final String exerciseRepoName = projectKey.toLowerCase() + "-" + RepositoryType.TEMPLATE.getName();
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setBuildPlanId(projectKey + "-" + templatePlanName);
        templateParticipation.setRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(projectKey, exerciseRepoName).toString());
        templateParticipation.setProgrammingExercise(newExercise);
        newExercise.setTemplateParticipation(templateParticipation);
        templateParticipationRepository.save(templateParticipation);
    }
}
