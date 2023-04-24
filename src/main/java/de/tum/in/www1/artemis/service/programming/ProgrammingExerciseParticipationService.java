package de.tum.in.www1.artemis.service.programming;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Optional;

import javax.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.BuildPlanType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.VersionControlException;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.vcs.VersionControlService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseParticipationService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final ParticipationRepository participationRepository;

    private final TeamRepository teamRepository;

    private final Optional<VersionControlService> versionControlService;

    private final GitService gitService;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, TeamRepository teamRepository, GitService gitService, Optional<VersionControlService> versionControlService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
    }

    /**
     * Retrieve the solution participation of the given programming exercise.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @return the SolutionProgrammingExerciseParticipation of programming exercise.
     * @throws EntityNotFoundException if the SolutionParticipation can't be found (could be that the programming exercise does not exist or it does not have a
     *                                     SolutionParticipation).
     */
    // TODO: move into solutionParticipationRepository
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
     * @throws EntityNotFoundException if the TemplateParticipation can't be found (could be that the programming exercise does not exist or it does not have a
     *                                     TemplateParticipation).
     */
    // TODO: move into templateParticipationRepository
    public TemplateProgrammingExerciseParticipation findTemplateParticipationByProgrammingExerciseId(Long programmingExerciseId) throws EntityNotFoundException {
        Optional<TemplateProgrammingExerciseParticipation> templateParticipation = templateParticipationRepository.findByProgrammingExerciseId(programmingExerciseId);
        if (templateParticipation.isEmpty()) {
            throw new EntityNotFoundException("Could not find solution participation for programming exercise with id " + programmingExerciseId);
        }
        return templateParticipation.get();
    }

    /**
     * Tries to retrieve a team participation for the given exercise and team short name.
     *
     * @param exercise        the exercise for which to find a participation.
     * @param teamShortName   of the team to which the participation belongs.
     * @param withSubmissions true if the participation should be fetched with its submissions.
     * @return the participation for the given exercise and team.
     * @throws EntityNotFoundException if the team participation was not found.
     */
    public ProgrammingExerciseStudentParticipation findTeamParticipationByExerciseAndTeamShortNameOrThrow(ProgrammingExercise exercise, String teamShortName,
            boolean withSubmissions) {
        Team team = teamRepository.findOneByExerciseCourseIdAndShortNameOrThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId(), teamShortName);

        Optional<ProgrammingExerciseStudentParticipation> participationOptional;

        if (withSubmissions) {
            participationOptional = studentParticipationRepository.findWithSubmissionsByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }
        else {
            participationOptional = studentParticipationRepository.findByExerciseIdAndTeamId(exercise.getId(), team.getId());
        }

        if (participationOptional.isEmpty()) {
            throw new EntityNotFoundException("Participation could not be found by exerciseId " + exercise.getId() + " and team short name " + teamShortName);
        }

        return participationOptional.get();
    }

    /**
     * Tries to retrieve a student participation for the given exercise and username and test run flag.
     *
     * @param exercise        the exercise for which to find a participation.
     * @param username        of the user to which the participation belongs.
     * @param isTestRun       true if the participation is a test run participation.
     * @param withSubmissions true if the participation should be loaded with its submissions.
     * @return the participation for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    @NotNull
    public ProgrammingExerciseStudentParticipation findStudentParticipationByExerciseAndStudentLoginAndTestRunOrThrow(ProgrammingExercise exercise, String username,
            boolean isTestRun, boolean withSubmissions) {

        Optional<ProgrammingExerciseStudentParticipation> participationOptional;

        if (withSubmissions) {
            participationOptional = studentParticipationRepository.findWithSubmissionsByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), username, isTestRun);
        }
        else {
            participationOptional = studentParticipationRepository.findByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), username, isTestRun);
        }

        if (participationOptional.isEmpty()) {
            throw new EntityNotFoundException("Participation could not be found by exerciseId " + exercise.getId() + " and user " + username);
        }

        return participationOptional.get();
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

    /**
     * Try to find a programming exercise participation for the given id.
     * It contains the last submission which might be illegal!
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
     * Setup the initial solution participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URL. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     */
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
    public void setupInitialTemplateParticipation(ProgrammingExercise newExercise) {
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
     * @param participation       the programming exercise student participation whose repository should be locked
     * @throws VersionControlException if locking was not successful, e.g. if the repository was already locked
     */
    public void lockStudentRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.get().setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUrl(), programmingExercise.getProjectKey(), participation.getStudents());
        }
        else {
            log.warn("Cannot lock student repository for participation {} because the repository was not copied yet!", participation.getId());
        }
    }

    /**
     * Unlock the repository associated with a programming participation
     *
     * @param programmingExercise the programming exercise
     * @param participation       the programming exercise student participation whose repository should be unlocked
     * @throws VersionControlException if unlocking was not successful, e.g. if the repository was already unlocked
     */
    public void unlockStudentRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            // TODO: this calls protect branches which might not be necessary if the branches have already been protected during "start exercise" which is typically the case
            versionControlService.get().configureRepository(programmingExercise, participation, true);
        }
        else {
            log.warn("Cannot unlock student repository for participation {} because the repository was not copied yet!", participation.getId());
        }
    }

    /**
     * Stashes all changes, which were not submitted/committed before the due date, of a programming participation
     *
     * @param programmingExercise exercise with information about the due date
     * @param participation       student participation whose not submitted changes will be stashed
     */
    public void stashChangesInStudentRepositoryAfterDueDateHasPassed(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            try {
                // Note: exam exercise do not have a due date, this method should only be invoked directly after the due date so now check is needed here
                Repository repo = gitService.getOrCheckoutRepository(participation);
                gitService.stashChanges(repo);
            }
            catch (GitAPIException e) {
                log.error("Stashing student repository for participation {} in exercise '{}' did not work as expected: {}", participation.getId(), programmingExercise.getTitle(),
                        e.getMessage());
            }
        }
        else {
            log.warn("Cannot stash student repository for participation {} because the repository was not copied yet!", participation.getId());
        }
    }

    /**
     * Replaces all files except the .git folder of the target repository with the files from the source repository
     *
     * @param targetURL the repository where all files should be replaced
     * @param sourceURL the repository that should be used as source for all files
     */
    public void resetRepository(VcsRepositoryUrl targetURL, VcsRepositoryUrl sourceURL) throws GitAPIException, IOException {
        Repository targetRepo = gitService.getOrCheckoutRepository(targetURL, true);
        Repository sourceRepo = gitService.getOrCheckoutRepository(sourceURL, true);

        // Replace everything but the files corresponding to git (such as the .git folder or the .gitignore file)
        FilenameFilter filter = (dir, name) -> !dir.isDirectory() || !name.contains(".git");
        for (java.io.File file : targetRepo.getLocalPath().toFile().listFiles(filter)) {
            FileSystemUtils.deleteRecursively(file);
        }
        for (java.io.File file : sourceRepo.getLocalPath().toFile().listFiles(filter)) {
            if (file.isDirectory()) {
                FileUtils.copyDirectory(file, targetRepo.getLocalPath().resolve(file.toPath().getFileName()).toFile());
            }
            else {
                FileUtils.copyFile(file, targetRepo.getLocalPath().resolve(file.toPath().getFileName()).toFile());
            }
        }

        gitService.stageAllChanges(targetRepo);
        gitService.commitAndPush(targetRepo, "Reset Exercise", true, null);
    }
}
