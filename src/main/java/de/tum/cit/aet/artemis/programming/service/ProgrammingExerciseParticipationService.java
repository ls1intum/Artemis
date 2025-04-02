package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseParticipationService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseParticipationService.class);

    private final ProgrammingExerciseStudentParticipationRepository studentParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateParticipationRepository;

    private final ParticipationRepository participationRepository;

    private final TeamRepository teamRepository;

    private final Optional<VersionControlService> versionControlService;

    private final GitService gitService;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, TeamRepository teamRepository, GitService gitService, Optional<VersionControlService> versionControlService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
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
     * Tries to retrieve a student participation for the given team exercise and user
     *
     * @param exercise the exercise for which to find a participation.
     * @param user     the user who is member of the team to which the participation belongs.
     * @return the participation for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    public Optional<ProgrammingExerciseStudentParticipation> findTeamParticipationByExerciseAndUser(ProgrammingExercise exercise, User user) {
        return studentParticipationRepository.findTeamParticipationByExerciseIdAndStudentId(exercise.getId(), user.getId());
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
     * Tries to retrieve all student participation for the given exercise id and username.
     *
     * @param exercise the exercise for which to find a participation
     * @param username of the user to which the participation belongs.
     * @return the participations for the given exercise and user.
     * @throws EntityNotFoundException if there is no participation for the given exercise and user.
     */
    @NotNull
    public List<ProgrammingExerciseStudentParticipation> findStudentParticipationsByExerciseAndStudentId(Exercise exercise, String username) throws EntityNotFoundException {
        return studentParticipationRepository.findAllByExerciseIdAndStudentLogin(exercise.getId(), username);
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
     * the correct build plan ID and repository URI. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     */
    public void setupInitialSolutionParticipation(ProgrammingExercise newExercise) {
        final String solutionRepoName = newExercise.generateRepositoryName(RepositoryType.SOLUTION);
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        newExercise.setSolutionParticipation(solutionParticipation);
        solutionParticipation.setBuildPlanId(newExercise.generateBuildPlanId(BuildPlanType.SOLUTION));
        solutionParticipation.setRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(newExercise.getProjectKey(), solutionRepoName).toString());
        solutionParticipation.setProgrammingExercise(newExercise);
        solutionParticipationRepository.save(solutionParticipation);
    }

    /**
     * Setup the initial template participation for an exercise. Creates the new participation entity and sets
     * the correct build plan ID and repository URI. Saves the participation after all values have been set.
     *
     * @param newExercise The new exercise for which a participation should be generated
     */
    public void setupInitialTemplateParticipation(ProgrammingExercise newExercise) {
        final String exerciseRepoName = newExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        TemplateProgrammingExerciseParticipation templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.setBuildPlanId(newExercise.generateBuildPlanId(BuildPlanType.TEMPLATE));
        templateParticipation.setRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(newExercise.getProjectKey(), exerciseRepoName).toString());
        templateParticipation.setProgrammingExercise(newExercise);
        newExercise.setTemplateParticipation(templateParticipation);
        templateParticipationRepository.save(templateParticipation);
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
    public void resetRepository(VcsRepositoryUri targetURL, VcsRepositoryUri sourceURL) throws GitAPIException, IOException {
        Repository targetRepo = gitService.getOrCheckoutRepository(targetURL, true);
        Repository sourceRepo = gitService.getOrCheckoutRepository(sourceURL, true);

        // Replace everything but the files corresponding to git (such as the .git folder or the .gitignore file)
        FilenameFilter filter = (dir, name) -> !dir.isDirectory() || !name.contains(".git");
        final var targetRepoFiles = Optional.ofNullable(targetRepo.getLocalPath().toFile().listFiles(filter)).orElseGet(() -> new File[0]);
        for (java.io.File file : targetRepoFiles) {
            FileSystemUtils.deleteRecursively(file);
        }
        final var sourceRepoFiles = Optional.ofNullable(sourceRepo.getLocalPath().toFile().listFiles(filter)).orElseGet(() -> new File[0]);
        for (java.io.File file : sourceRepoFiles) {
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

    /**
     * Get the participation for a given repository url and a repository type or user name. This method is used by the local VC system to get the
     * participation for logging operations on the repository.
     *
     * @param repositoryTypeOrUserName the name of the user or the type of the repository
     * @param repositoryURI            the participation's repository URL
     * @param exercise                 the exercise the participation belongs to
     * @return the participation belonging to the provided repositoryURI and repository type or username
     */
    public ProgrammingExerciseParticipation fetchParticipationWithSubmissionsByRepository(String repositoryTypeOrUserName, String repositoryURI, ProgrammingExercise exercise) {
        var repositoryURL = repositoryURI.replace("/git-upload-pack", "").replace("/git-receive-pack", "");
        if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString()) || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            return solutionParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
        }
        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            return templateParticipationRepository.findWithSubmissionsByRepositoryUriElseThrow(repositoryURL);
        }
        return studentParticipationRepository.findWithSubmissionsByRepositoryUriElseThrow(repositoryURL);

    }

    public ProgrammingExerciseParticipation retrieveSolutionParticipation(Exercise exercise) {
        return solutionParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
    }

    /**
     * Get the participation for a given repository url and a repository type or user name. This method is used by the local VC system to get the
     * participation for logging operations on the repository.
     *
     * @param repositoryTypeOrUserName the name of the user or the type of the repository
     * @param repositoryURI            the participation's repository URL
     * @param exercise                 the exercise the participation belongs to
     * @return the participation belonging to the provided repositoryURI and repository type or username
     */
    public ProgrammingExerciseParticipation fetchParticipationByRepository(String repositoryTypeOrUserName, String repositoryURI, ProgrammingExercise exercise) {
        var repositoryURL = repositoryURI.replace("/git-upload-pack", "").replace("/git-receive-pack", "");
        if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString()) || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString())) {
            return solutionParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
        }
        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            return templateParticipationRepository.findByRepositoryUriElseThrow(repositoryURL);
        }
        return studentParticipationRepository.findByRepositoryUriElseThrow(repositoryURL);
    }

    /**
     * Get the commits information for the given participation.
     *
     * @param participation the participation for which to get the commits.
     * @return a list of CommitInfo DTOs containing author, timestamp, commit-hash and commit message.
     */
    public List<CommitInfoDTO> getCommitInfos(ProgrammingExerciseParticipation participation) {
        try {
            return gitService.getCommitInfos(participation.getVcsRepositoryUri());
        }
        catch (GitAPIException e) {
            log.error("Could not get commit infos for participation {} with repository uri {}", participation.getId(), participation.getVcsRepositoryUri());
            return List.of();
        }
    }

    /**
     * Get the commits information for the given auxiliary repository.
     *
     * @param auxiliaryRepository the auxiliary repository for which to get the commits.
     * @return a list of CommitInfo DTOs containing author, timestamp, commit-hash and commit message.
     */
    public List<CommitInfoDTO> getAuxiliaryRepositoryCommitInfos(AuxiliaryRepository auxiliaryRepository) {
        try {
            return gitService.getCommitInfos(auxiliaryRepository.getVcsRepositoryUri());
        }
        catch (GitAPIException e) {
            log.error("Could not get commit infos for auxiliaryRepository {} with repository uri {}", auxiliaryRepository.getId(), auxiliaryRepository.getVcsRepositoryUri());
            return List.of();
        }
    }

    /**
     * Get the commits information for the test repository of the given participation's exercise.
     *
     * @param participation the participation for which to get the commits.
     * @return a list of CommitInfo DTOs containing author, timestamp, commit-hash and commit message.
     */
    public List<CommitInfoDTO> getCommitInfosTestRepo(ProgrammingExerciseParticipation participation) {
        ProgrammingExercise exercise = (ProgrammingExercise) participation.getExercise();
        try {
            return gitService.getCommitInfos(exercise.getVcsTestRepositoryUri());
        }
        catch (GitAPIException e) {
            log.error("Could not get commit infos for test repository with participation id {}", participation.getId());
            return List.of();
        }
    }

    /**
     * Returns the matching template, solution or student participation for a given build plan key.
     *
     * @param planKey the build plan key
     * @return the matching participation
     */
    @Nullable
    public ProgrammingExerciseParticipation getParticipationWithResults(String planKey) {
        // we have to support template, solution and student build plans here
        if (planKey.endsWith("-" + BuildPlanType.TEMPLATE.getName())) {
            return templateProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        else if (planKey.endsWith("-" + BuildPlanType.SOLUTION.getName())) {
            return solutionProgrammingExerciseParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        List<ProgrammingExerciseStudentParticipation> participations = programmingExerciseStudentParticipationRepository
                .findWithResultsAndExerciseAndTeamStudentsByBuildPlanId(planKey);
        ProgrammingExerciseStudentParticipation participation = null;
        if (!participations.isEmpty()) {
            participation = participations.getFirst();
            if (participations.size() > 1) {
                // in the rare case of multiple participations, take the latest one.
                for (ProgrammingExerciseStudentParticipation otherParticipation : participations) {
                    if (otherParticipation.getInitializationDate().isAfter(participation.getInitializationDate())) {
                        participation = otherParticipation;
                    }
                }
            }
        }
        return participation;
    }
}
