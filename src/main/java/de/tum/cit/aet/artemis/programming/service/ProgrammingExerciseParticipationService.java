package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.FilenameFilter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.programming.domain.BuildPlanType;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlRepositoryPermission;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.web.rest.dto.CommitInfoDTO;

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

    private final AuthorizationCheckService authorizationCheckService;

    private final UserRepository userRepository;

    private final AuxiliaryRepositoryService auxiliaryRepositoryService;

    public ProgrammingExerciseParticipationService(SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateParticipationRepository, ProgrammingExerciseStudentParticipationRepository studentParticipationRepository,
            ParticipationRepository participationRepository, TeamRepository teamRepository, GitService gitService, Optional<VersionControlService> versionControlService,
            AuthorizationCheckService authorizationCheckService, UserRepository userRepository, AuxiliaryRepositoryService auxiliaryRepositoryService) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.solutionParticipationRepository = solutionParticipationRepository;
        this.templateParticipationRepository = templateParticipationRepository;
        this.participationRepository = participationRepository;
        this.teamRepository = teamRepository;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.authorizationCheckService = authorizationCheckService;
        this.userRepository = userRepository;
        this.auxiliaryRepositoryService = auxiliaryRepositoryService;
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

        Optional<ProgrammingExerciseStudentParticipation> participationOptional;

        // It is important to fetch all students of the team here, because the local VC and local CI system use this participation to check if the authenticated user is part of the
        // team.
        if (withSubmissions) {
            participationOptional = studentParticipationRepository.findWithSubmissionsAndEagerStudentsByExerciseIdAndTeamShortName(exercise.getId(), teamShortName);
        }
        else {
            participationOptional = studentParticipationRepository.findWithEagerStudentsByExerciseIdAndTeamShortName(exercise.getId(), teamShortName);
        }

        if (participationOptional.isEmpty()) {
            throw new EntityNotFoundException("Participation could not be found by exerciseId " + exercise.getId() + " and team short name " + teamShortName);
        }

        return participationOptional.get();
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
     * Lock the repository associated with a programming participation.
     *
     * @param programmingExercise the programming exercise
     * @param participation       the programming exercise student participation whose repository should be locked
     * @throws VersionControlException if locking was not successful, e.g. if the repository was already locked
     */
    public void lockStudentRepository(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            versionControlService.orElseThrow().setRepositoryPermissionsToReadOnly(participation.getVcsRepositoryUri(), programmingExercise.getProjectKey(),
                    participation.getStudents());
        }
        else {
            log.warn("Cannot lock student repository for participation {} because the repository was not copied yet!", participation.getId());
        }
    }

    /**
     * Asynchronously lock the repositories of all programming exercises of the given student exam, e.g. because the student handed in early
     *
     * @param user        the user to which the student exam belongs
     * @param studentExam the student exam for which the lock operation should be executed
     */
    @Async
    public void lockStudentRepositories(User user, StudentExam studentExam) {
        // Only lock programming exercises when the student submitted early in real exams. Otherwise, the lock operations were already scheduled/executed.
        // Always lock test exams since there is no locking operation scheduled (also see StudentExamService:457)
        if (studentExam.isTestExam() || (studentExam.getIndividualEndDate() != null && ZonedDateTime.now().isBefore(studentExam.getIndividualEndDate()))) {
            // Use the programming exercises in the DB to lock the repositories (for safety)
            for (Exercise exercise : studentExam.getExercises()) {
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    try {
                        log.debug("lock student repositories for {}", user);
                        var participation = findStudentParticipationByExerciseAndStudentId(programmingExercise, user.getLogin());
                        lockStudentRepository(programmingExercise, participation);
                    }
                    catch (Exception e) {
                        log.error("Locking programming exercise {} submitted manually by {} failed", exercise.getId(), user.getLogin(), e);
                    }
                }
            }
        }
    }

    /**
     * Lock a student participation. This is necessary if the student is not allowed to submit either from the online editor or from their local Git client.
     * This is the case, if the start date of the exercise is in the future, if the due date is in the past, or if the student has reached the submission limit.
     *
     * @param participation the participation to be locked
     */
    public void lockStudentParticipation(ProgrammingExerciseStudentParticipation participation) {
        // Update the locked field for the given participation in the database.
        studentParticipationRepository.updateLockedById(participation.getId(), true);
        // Also set the correct value on the participation object in case the caller uses this participation for further processing.
        participation.setLocked(true);
    }

    /**
     * Lock the repository associated with a programming participation and the participation itself.
     *
     * @param programmingExercise the programming exercise
     * @param participation       the programming exercise student participation whose repository should be locked
     * @throws VersionControlException if locking was not successful, e.g. if the repository was already locked
     */
    public void lockStudentRepositoryAndParticipation(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation) {
        lockStudentRepository(programmingExercise, participation);
        lockStudentParticipation(participation);
    }

    /**
     * Unlock a student repository. This is necessary if the student is now allowed to submit either from the online editor or from their local Git client.
     * This is the case, if the start date of the exercise is in the past, if the due date is in the future, and if the student has not reached the submission limit yet.
     *
     * @param participation the participation whose repository should be unlocked
     */
    public void unlockStudentRepository(ProgrammingExerciseStudentParticipation participation) {
        if (participation.getInitializationState().hasCompletedState(InitializationState.REPO_CONFIGURED)) {
            for (User user : participation.getStudents()) {
                versionControlService.orElseThrow().addMemberToRepository(participation.getVcsRepositoryUri(), user, VersionControlRepositoryPermission.REPO_WRITE);
            }
        }
        else {
            log.warn("Cannot unlock student repository for participation {} because the repository was not copied yet!", participation.getId());
        }
    }

    /**
     * Unlock a student participation. This is necessary if the student is now allowed to submit either from the online editor or from their local Git client.
     * This is the case, if the start date of the exercise is in the past, if the due date is in the future, and if the student has not reached the submission limit yet.
     *
     * @param participation the participation to be unlocked
     */
    public void unlockStudentParticipation(ProgrammingExerciseStudentParticipation participation) {
        // Update the locked field for the given participation in the database.
        studentParticipationRepository.updateLockedById(participation.getId(), false);
        // Also set the correct value on the participation object in case the caller uses this participation for further processing.
        participation.setLocked(false);
    }

    /**
     * Unlock the repository associated with a programming participation and the participation itself.
     *
     * @param participation the programming exercise student participation whose repository should be unlocked
     * @throws VersionControlException if unlocking was not successful, e.g. if the repository was already unlocked
     */
    public void unlockStudentRepositoryAndParticipation(ProgrammingExerciseStudentParticipation participation) {
        unlockStudentRepository(participation);
        unlockStudentParticipation(participation);
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

    /**
     * Get the participation for a given exercise and a repository type or user name. This method is used by the local VC system and by the local CI system to get the
     * participation.
     *
     * @param exercise                 the exercise for which to get the participation.
     * @param repositoryTypeOrUserName the repository type ("exercise", "solution", or "tests") or username (e.g. "artemis_test_user_1") as extracted from the repository URI.
     * @param isPracticeRepository     whether the repository is a practice repository, i.e. the repository URI contains "-practice-".
     * @param withSubmissions          whether submissions should be loaded with the participation. This is needed for the local CI system.
     * @return the participation.
     * @throws EntityNotFoundException if the participation could not be found.
     */
    public ProgrammingExerciseParticipation getParticipationForRepository(ProgrammingExercise exercise, String repositoryTypeOrUserName, boolean isPracticeRepository,
            boolean withSubmissions) {

        boolean isAuxiliaryRepository = auxiliaryRepositoryService.isAuxiliaryRepositoryOfExercise(repositoryTypeOrUserName, exercise);

        // For pushes to the tests repository, the solution repository is built first, and thus we need the solution participation.
        // Can possibly be used by auxiliary repositories
        if (repositoryTypeOrUserName.equals(RepositoryType.SOLUTION.toString()) || repositoryTypeOrUserName.equals(RepositoryType.TESTS.toString()) || isAuxiliaryRepository) {
            if (withSubmissions) {
                return solutionParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else {
                return solutionParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
        }

        if (repositoryTypeOrUserName.equals(RepositoryType.TEMPLATE.toString())) {
            if (withSubmissions) {
                return templateParticipationRepository.findWithEagerResultsAndSubmissionsByProgrammingExerciseIdElseThrow(exercise.getId());
            }
            else {
                return templateParticipationRepository.findByProgrammingExerciseIdElseThrow(exercise.getId());
            }
        }

        if (exercise.isTeamMode()) {
            // The repositoryTypeOrUserName is the team short name.
            // For teams, there is no practice participation.
            return findTeamParticipationByExerciseAndTeamShortNameOrThrow(exercise, repositoryTypeOrUserName, withSubmissions);
        }

        // If the exercise is an exam exercise and the repository's owner is at least an editor, the repository could be a test run repository, or it could be the instructor's
        // assignment repository.
        // There is no way to tell from the repository URI, and only one participation will be created, even if both are used.
        // This participation has "testRun = true" set if the test run was created first, and "testRun = false" set if the instructor's assignment repository was created first.
        // If the exercise is an exam exercise, and the repository's owner is at least an editor, get the participation without regard for the testRun flag.
        boolean isExamEditorRepository = exercise.isExamExercise()
                && authorizationCheckService.isAtLeastEditorForExercise(exercise, userRepository.getUserByLoginElseThrow(repositoryTypeOrUserName));
        if (isExamEditorRepository) {
            if (withSubmissions) {
                return studentParticipationRepository.findWithSubmissionsByExerciseIdAndStudentLoginOrThrow(exercise.getId(), repositoryTypeOrUserName);
            }

            return studentParticipationRepository.findByExerciseIdAndStudentLoginOrThrow(exercise.getId(), repositoryTypeOrUserName);
        }

        return findStudentParticipationByExerciseAndStudentLoginAndTestRunOrThrow(exercise, repositoryTypeOrUserName, isPracticeRepository, withSubmissions);
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
}
