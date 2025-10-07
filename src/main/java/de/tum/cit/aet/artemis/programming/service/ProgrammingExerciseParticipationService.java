package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType;
import de.tum.cit.aet.artemis.programming.dto.CommitInfoDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Profile(PROFILE_CORE)
@Lazy
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

    private final ResultRepository resultRepository;

    private final SubmissionRepository submissionRepository;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, TeamRepository teamRepository, GitService gitService, Optional<VersionControlService> versionControlService,
            ResultRepository resultRepository, SubmissionRepository submissionRepository) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.resultRepository = resultRepository;
        this.submissionRepository = submissionRepository;
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
     * Replaces all files except the .git folder of the target repository with the files from the source repository
     *
     * @param targetUri the repository where all files should be replaced
     * @param sourceUri the repository that should be used as source for all files
     */
    public void resetRepository(LocalVCRepositoryUri targetUri, LocalVCRepositoryUri sourceUri) throws GitAPIException, IOException {
        Repository targetRepo = gitService.getOrCheckoutRepository(targetUri, true, true);
        Repository sourceRepo = gitService.getOrCheckoutRepository(sourceUri, true, true);

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
     * Get the commits information for the given repository URI: can be a template, solution, tests, auxiliary or student repository.
     *
     * @param localVCRepositoryUri the repository URI for which to get the commits.
     * @return a list of CommitInfo DTOs containing author, timestamp, commit-hash and commit message.
     */
    // TODO: use some kind of paging mechanism
    public List<CommitInfoDTO> getCommitInfos(LocalVCRepositoryUri localVCRepositoryUri) {
        try {
            return gitService.getCommitInfos(localVCRepositoryUri);
        }
        catch (GitAPIException e) {
            log.error("Could not get commit infos for repository with uri {}", localVCRepositoryUri);
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
            return templateParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        else if (planKey.endsWith("-" + BuildPlanType.SOLUTION.getName())) {
            return solutionParticipationRepository.findByBuildPlanIdWithResults(planKey).orElse(null);
        }
        List<ProgrammingExerciseStudentParticipation> participations = studentParticipationRepository.findWithResultsAndExerciseAndTeamStudentsByBuildPlanId(planKey);
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

    /**
     * Retrieves the {@link ProgrammingExerciseStudentParticipation} for the given ID, including
     * its latest {@link Submission} and the most recent {@link Result} with feedback (if available).
     *
     * <p>
     * If no submission exists for the participation, the returned participation will contain
     * an empty set of submissions. If a submission exists but no result with feedback is found,
     * the submission will contain an empty list of results.
     * </p>
     *
     * @param participationId the ID of the student participation to retrieve
     * @return the participation enriched with its latest submission and corresponding result (if available)
     * @throws EntityNotFoundException if no participation exists with the given ID
     */
    public ProgrammingExerciseStudentParticipation findStudentParticipationWithLatestSubmissionResultAndFeedbacksElseThrow(long participationId) throws EntityNotFoundException {
        ProgrammingExerciseStudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        Optional<Submission> latestSubmissionOptional = submissionRepository.findLatestSubmissionByParticipationId(participationId);
        if (latestSubmissionOptional.isEmpty()) {
            participation.setSubmissions(Set.of());
            return participation;
        }
        Submission latestSubmission = latestSubmissionOptional.get();
        Optional<Result> latestResultOptional = resultRepository.findLatestResultWithFeedbacksBySubmissionId(latestSubmission.getId(), ZonedDateTime.now());
        latestResultOptional.ifPresentOrElse(latestResult -> latestSubmission.setResults(List.of(latestResult)), () -> latestSubmission.setResults(List.of()));
        participation.setSubmissions(Set.of(latestSubmission));
        return participation;
    }
}
