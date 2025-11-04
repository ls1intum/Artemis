package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseAthenaConfig;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.structureoraclegenerator.OracleGenerator;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class ProgrammingExerciseCreationUpdateService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseCreationUpdateService.class);

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private final ProgrammingExerciseRepositoryService programmingExerciseRepositoryService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ExerciseService exerciseService;

    private final ChannelService channelService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService;

    private final ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    private final ProgrammingExerciseAtlasIrisService programmingExerciseAtlasIrisService;

    private final Optional<VersionControlService> versionControlService;

    private final GitService gitService;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    private final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final ParticipationRepository participationRepository;

    public ProgrammingExerciseCreationUpdateService(ProgrammingExerciseRepositoryService programmingExerciseRepositoryService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, ProgrammingSubmissionService programmingSubmissionService,
            UserRepository userRepository, ExerciseService exerciseService, ProgrammingExerciseRepository programmingExerciseRepository, ChannelService channelService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService,
            ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService, ProgrammingExerciseAtlasIrisService programmingExerciseAtlasIrisService,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<VersionControlService> versionControlService, ParticipationRepository participationRepository, GitService gitService) {
        this.programmingExerciseRepositoryService = programmingExerciseRepositoryService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.programmingSubmissionService = programmingSubmissionService;
        this.userRepository = userRepository;
        this.exerciseService = exerciseService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.channelService = channelService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.programmingExerciseBuildPlanService = programmingExerciseBuildPlanService;
        this.programmingExerciseCreationScheduleService = programmingExerciseCreationScheduleService;
        this.programmingExerciseAtlasIrisService = programmingExerciseAtlasIrisService;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.versionControlService = versionControlService;
        this.participationRepository = participationRepository;
        this.gitService = gitService;
    }

    /**
     * Setups the context of a new programming exercise. This includes:
     * <ul>
     * <li>The VCS project</li>
     * <li>All repositories (test, exercise, solution)</li>
     * <li>The template and solution participation</li>
     * <li>VCS webhooks</li>
     * </ul>
     * The exercise gets set up in the following order:
     * <ol>
     * <li>Create all repositories for the new exercise</li>
     * <li>Setup template and push it to the repositories</li>
     * <li>Setup new build plans for exercise</li>
     * <li>Add all webhooks</li>
     * <li>Init scheduled jobs for exercise maintenance</li>
     * </ol>
     *
     * @param programmingExercise The programmingExercise that should be setup
     * @return The new setup exercise
     * @throws GitAPIException If something during the communication with the remote Git repository went wrong
     * @throws IOException     If the template files couldn't be read
     */
    public ProgrammingExercise createProgrammingExercise(ProgrammingExercise programmingExercise) throws GitAPIException, IOException {
        final User exerciseCreator = userRepository.getUser();

        // The client sends a solution and template participation object (filled with null values) when creating a programming exercise.
        // When saving the object leads to an exception at runtime.
        // As the participations objects are just dummy values representing the data structure in the client, we set this to null.
        // See https://github.com/ls1intum/Artemis/pull/7451/files#r1459228917
        programmingExercise.setSolutionParticipation(null);
        programmingExercise.setTemplateParticipation(null);
        programmingExercise.getBuildConfig().setId(null);

        // We save once in order to generate an id for the programming exercise
        var savedBuildConfig = programmingExerciseBuildConfigRepository.saveAndFlush(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);

        ExerciseAthenaConfig athenaConfig = programmingExercise.getAthenaConfig();
        var savedProgrammingExercise = exerciseService.saveWithCompetencyLinks(programmingExercise, programmingExerciseRepository::saveForCreation);
        exerciseService.saveAthenaConfig(savedProgrammingExercise, athenaConfig);

        savedProgrammingExercise.getBuildConfig().setProgrammingExercise(savedProgrammingExercise);
        programmingExerciseBuildConfigRepository.save(savedProgrammingExercise.getBuildConfig());
        savedProgrammingExercise.generateAndSetProjectKey();
        savedProgrammingExercise.getBuildConfig().setBranch(defaultBranch);

        programmingExerciseRepositoryService.createRepositoriesForNewExercise(savedProgrammingExercise);
        initParticipations(savedProgrammingExercise);

        setURLsAndBuildPlanIDsForNewExercise(savedProgrammingExercise);

        connectBaseParticipationsToExerciseAndSave(savedProgrammingExercise);

        programmingExerciseBuildConfigRepository.saveAndFlush(savedProgrammingExercise.getBuildConfig());
        savedProgrammingExercise = programmingExerciseRepository.saveForCreation(savedProgrammingExercise);

        connectAuxiliaryRepositoriesToExercise(savedProgrammingExercise);

        programmingExerciseRepositoryService.setupExerciseTemplate(savedProgrammingExercise, exerciseCreator);

        programmingSubmissionService.createInitialSubmissions(savedProgrammingExercise);

        // Make sure that plagiarism detection config does not use existing id
        Optional.ofNullable(savedProgrammingExercise.getPlagiarismDetectionConfig()).ifPresent(it -> it.setId(null));

        programmingExerciseBuildPlanService.addDefaultBuildPlanConfigForLocalCI(savedProgrammingExercise);

        channelService.createExerciseChannel(savedProgrammingExercise, Optional.ofNullable(programmingExercise.getChannelName()));

        programmingExerciseBuildPlanService.setupBuildPlansForNewExercise(savedProgrammingExercise);
        savedProgrammingExercise = programmingExerciseRepository.findForCreationByIdElseThrow(savedProgrammingExercise.getId());

        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        programmingExerciseCreationScheduleService.performScheduleOperationsAndCheckNotifications(savedProgrammingExercise);
        programmingExerciseAtlasIrisService.updateCompetencyProgressOnCreationAndEnableIris(savedProgrammingExercise);

        return programmingExerciseRepository.saveForCreation(savedProgrammingExercise);
    }

    /**
     * This method connects the new programming exercise with the template and solution participation
     *
     * @param programmingExercise the new programming exercise
     */
    public void connectBaseParticipationsToExerciseAndSave(ProgrammingExercise programmingExercise) {
        var templateParticipation = programmingExercise.getTemplateParticipation();
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        templateParticipation.setProgrammingExercise(programmingExercise);
        solutionParticipation.setProgrammingExercise(programmingExercise);
        templateParticipation = templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        solutionParticipation = solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
        programmingExercise.setTemplateParticipation(templateParticipation);
        programmingExercise.setSolutionParticipation(solutionParticipation);
    }

    private void connectAuxiliaryRepositoriesToExercise(ProgrammingExercise exercise) {
        List<AuxiliaryRepository> savedRepositories = new ArrayList<>(exercise.getAuxiliaryRepositories().stream().filter(repo -> repo.getId() != null).toList());
        exercise.getAuxiliaryRepositories().stream().filter(repository -> repository.getId() == null).forEach(repository -> {
            // We have to disconnect the exercise from the auxiliary repository
            // since the auxiliary repositories of an exercise are represented as
            // a sorted collection (list).
            repository.setExercise(null);
            repository = auxiliaryRepositoryRepository.save(repository);
            repository.setExercise(exercise);
            savedRepositories.add(repository);
        });
        exercise.setAuxiliaryRepositories(savedRepositories);
    }

    private void setURLsAndBuildPlanIDsForNewExercise(ProgrammingExercise programmingExercise) {
        final var projectKey = programmingExercise.getProjectKey();
        final var templateParticipation = programmingExercise.getTemplateParticipation();
        final var solutionParticipation = programmingExercise.getSolutionParticipation();
        final var templatePlanId = programmingExercise.generateBuildPlanId(TEMPLATE);
        final var solutionPlanId = programmingExercise.generateBuildPlanId(SOLUTION);
        final var exerciseRepoName = programmingExercise.generateRepositoryName(RepositoryType.TEMPLATE);
        final var solutionRepoName = programmingExercise.generateRepositoryName(RepositoryType.SOLUTION);
        final var testRepoName = programmingExercise.generateRepositoryName(RepositoryType.TESTS);

        VersionControlService versionControl = versionControlService.orElseThrow();
        templateParticipation.setBuildPlanId(templatePlanId); // Set build plan id to newly created BaseBuild plan
        templateParticipation.setRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, exerciseRepoName).toString());
        solutionParticipation.setBuildPlanId(solutionPlanId);
        solutionParticipation.setRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, solutionRepoName).toString());
        programmingExercise.setTestRepositoryUri(versionControl.getCloneRepositoryUri(projectKey, testRepoName).toString());
    }

    private void setURLsForAuxiliaryRepositoriesOfExercise(ProgrammingExercise programmingExercise) {
        programmingExercise.getAuxiliaryRepositories().forEach(repo -> repo.setRepositoryUri(versionControlService.orElseThrow()
                .getCloneRepositoryUri(programmingExercise.getProjectKey(), programmingExercise.generateRepositoryName(repo.getName())).toString()));
    }

    /**
     * @param programmingExerciseBeforeUpdate the original programming exercise with its old values
     * @param updatedProgrammingExercise      the changed programming exercise with its new values
     * @param notificationText                optional text about the changes for a notification
     * @return the updates programming exercise from the database
     */
    public ProgrammingExercise updateProgrammingExercise(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise updatedProgrammingExercise,
            @Nullable String notificationText) throws JsonProcessingException {
        setURLsForAuxiliaryRepositoriesOfExercise(updatedProgrammingExercise);
        connectAuxiliaryRepositoriesToExercise(updatedProgrammingExercise);

        programmingExerciseBuildPlanService.updateBuildPlanForExercise(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        channelService.updateExerciseChannel(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        String problemStatementWithTestNames = updatedProgrammingExercise.getProblemStatement();
        programmingExerciseTaskService.replaceTestNamesWithIds(updatedProgrammingExercise);
        programmingExerciseBuildConfigRepository.save(updatedProgrammingExercise.getBuildConfig());

        ExerciseAthenaConfig athenaConfig = updatedProgrammingExercise.getAthenaConfig();
        ProgrammingExercise savedProgrammingExercise = exerciseService.saveWithCompetencyLinks(updatedProgrammingExercise, programmingExerciseRepository::save);
        exerciseService.saveAthenaConfig(savedProgrammingExercise, athenaConfig);

        // The returned value should use test case names since it gets send back to the client
        savedProgrammingExercise.setProblemStatement(problemStatementWithTestNames);

        participationRepository.removeIndividualDueDatesIfBeforeDueDate(savedProgrammingExercise, programmingExerciseBeforeUpdate.getDueDate());
        programmingExerciseTaskService.updateTasksFromProblemStatement(savedProgrammingExercise);

        if (programmingExerciseBeforeUpdate.isCourseExercise()) {
            programmingExerciseCreationScheduleService.scheduleOperations(updatedProgrammingExercise.getId());
        }

        exerciseService.notifyAboutExerciseChanges(programmingExerciseBeforeUpdate, updatedProgrammingExercise, notificationText);

        programmingExerciseAtlasIrisService.updateCompetencyProgressOnExerciseUpdateAndEnableIris(programmingExerciseBeforeUpdate, updatedProgrammingExercise);

        return savedProgrammingExercise;
    }

    /**
     * These methods set the values (initialization date and initialization state) of the template and solution participation.
     * If either participation is null, a new one will be created.
     *
     * @param programmingExercise The programming exercise
     */
    public void initParticipations(ProgrammingExercise programmingExercise) {
        var solutionParticipation = programmingExercise.getSolutionParticipation();
        var templateParticipation = programmingExercise.getTemplateParticipation();

        if (templateParticipation == null) {
            templateParticipation = new TemplateProgrammingExerciseParticipation();
            programmingExercise.setTemplateParticipation(templateParticipation);
        }
        if (solutionParticipation == null) {
            solutionParticipation = new SolutionProgrammingExerciseParticipation();
            programmingExercise.setSolutionParticipation(solutionParticipation);
        }

        solutionParticipation.setInitializationState(InitializationState.INITIALIZED);
        templateParticipation.setInitializationState(InitializationState.INITIALIZED);
        solutionParticipation.setInitializationDate(ZonedDateTime.now());
        templateParticipation.setInitializationDate(ZonedDateTime.now());
    }

    /**
     * Updates the timeline attributes of the given programming exercise
     *
     * @param updatedProgrammingExercise containing the changes that have to be saved
     * @param notificationText           optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     */
    public ProgrammingExercise updateTimeline(ProgrammingExercise updatedProgrammingExercise, @Nullable String notificationText) {
        ProgrammingExercise programmingExercise = programmingExerciseRepository.findByIdElseThrow(updatedProgrammingExercise.getId());

        // create slim copy of programmingExercise before the update - needed for notifications (only release date needed)
        ProgrammingExercise programmingExerciseBeforeUpdate = new ProgrammingExercise();
        programmingExerciseBeforeUpdate.setReleaseDate(programmingExercise.getReleaseDate());
        programmingExerciseBeforeUpdate.setStartDate(programmingExercise.getStartDate());
        programmingExerciseBeforeUpdate.setAssessmentDueDate(programmingExercise.getAssessmentDueDate());

        programmingExercise.setReleaseDate(updatedProgrammingExercise.getReleaseDate());
        programmingExercise.setStartDate(updatedProgrammingExercise.getStartDate());
        programmingExercise.setDueDate(updatedProgrammingExercise.getDueDate());
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(updatedProgrammingExercise.getBuildAndTestStudentSubmissionsAfterDueDate());
        programmingExercise.setAssessmentType(updatedProgrammingExercise.getAssessmentType());
        programmingExercise.setAssessmentDueDate(updatedProgrammingExercise.getAssessmentDueDate());
        programmingExercise.setExampleSolutionPublicationDate(updatedProgrammingExercise.getExampleSolutionPublicationDate());

        programmingExercise.validateDates();

        ProgrammingExercise savedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExerciseCreationScheduleService.createNotificationsOnUpdate(programmingExerciseBeforeUpdate, savedProgrammingExercise, notificationText);
        return savedProgrammingExercise;
    }

    /**
     * Updates the problem statement of the given programming exercise.
     *
     * @param programmingExercise The ProgrammingExercise of which the problem statement is updated.
     * @param problemStatement    markdown of the problem statement.
     * @param notificationText    optional text for a notification to all students about the update
     * @return the updated ProgrammingExercise object.
     * @throws EntityNotFoundException if there is no ProgrammingExercise for the given id.
     */
    public ProgrammingExercise updateProblemStatement(ProgrammingExercise programmingExercise, String problemStatement, @Nullable String notificationText)
            throws EntityNotFoundException {

        String oldProblemStatement = programmingExercise.getProblemStatement();
        programmingExercise.setProblemStatement(problemStatement);
        programmingExerciseTaskService.replaceTestNamesWithIds(programmingExercise);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);

        // Set the old problem statement again for notifyAboutExerciseChanges method, but don't save it
        programmingExercise.setProblemStatement(oldProblemStatement);

        programmingExerciseTaskService.updateTasksFromProblemStatement(updatedProgrammingExercise);

        exerciseService.notifyAboutExerciseChanges(programmingExercise, updatedProgrammingExercise, notificationText);

        return updatedProgrammingExercise;
    }

    /**
     * This method calls the StructureOracleGenerator, generates the string out of the JSON representation of the structure oracle of the programming exercise and returns true if
     * the file was updated or generated, false otherwise. This can happen if the contents of the file have not changed.
     *
     * @param solutionRepoUri The LocalVC URI of the solution repository.
     * @param exerciseRepoUri The LocalVC URI of the exercise repository.
     * @param testRepoUri     The LocalVC URI of the tests' repository.
     * @param testsPath       The path to the tests' folder, e.g. the path inside the repository where the structure oracle file will be saved in.
     * @param user            The user who has initiated the action
     * @return True, if the structure oracle was successfully generated or updated, false if no changes to the file were made.
     * @throws IOException     If the URLs cannot be converted to actual {@link Path paths}
     * @throws GitAPIException If the checkout fails
     */
    public boolean generateStructureOracleFile(LocalVCRepositoryUri solutionRepoUri, LocalVCRepositoryUri exerciseRepoUri, LocalVCRepositoryUri testRepoUri, String testsPath,
            User user) throws IOException, GitAPIException {
        Repository solutionRepository = gitService.getOrCheckoutRepository(solutionRepoUri, true, true);
        Repository exerciseRepository = gitService.getOrCheckoutRepository(exerciseRepoUri, true, true);
        Repository testRepository = gitService.getOrCheckoutRepository(testRepoUri, true, true);

        gitService.resetToOriginHead(solutionRepository);
        gitService.pullIgnoreConflicts(solutionRepository);
        gitService.resetToOriginHead(exerciseRepository);
        gitService.pullIgnoreConflicts(exerciseRepository);
        gitService.resetToOriginHead(testRepository);
        gitService.pullIgnoreConflicts(testRepository);

        Path solutionRepositoryPath = solutionRepository.getLocalPath().toRealPath();
        Path exerciseRepositoryPath = exerciseRepository.getLocalPath().toRealPath();
        Path structureOraclePath = Path.of(testRepository.getLocalPath().toRealPath().toString(), testsPath, "test.json");

        String structureOracleJSON = OracleGenerator.generateStructureOracleJSON(solutionRepositoryPath, exerciseRepositoryPath);
        return saveAndPushStructuralOracle(user, testRepository, structureOraclePath, structureOracleJSON);
    }

    private boolean saveAndPushStructuralOracle(User user, Repository testRepository, Path structureOraclePath, String structureOracleJSON) throws IOException {
        // If the oracle file does not already exist, then save the generated string to the file.
        // If it does, check if the contents of the existing file are the same as the generated one.
        // If they are, do not push anything and inform the user about it.
        // If not, then update the oracle file by rewriting it and push the changes.
        if (!Files.exists(structureOraclePath)) {
            try {
                FileUtils.writeStringToFile(structureOraclePath.toFile(), structureOracleJSON, StandardCharsets.UTF_8);
                gitService.stageAllChanges(testRepository);
                gitService.commitAndPush(testRepository, "Generate the structure oracle file.", true, user);
                return true;
            }
            catch (GitAPIException e) {
                log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                return false;
            }
        }
        else {
            Byte[] existingContents = ArrayUtils.toObject(Files.readAllBytes(structureOraclePath));
            Byte[] newContents = ArrayUtils.toObject(structureOracleJSON.getBytes());

            if (Arrays.deepEquals(existingContents, newContents)) {
                log.info("No changes to the oracle detected.");
                return false;
            }
            else {
                try {
                    FileUtils.writeStringToFile(structureOraclePath.toFile(), structureOracleJSON, StandardCharsets.UTF_8);
                    gitService.stageAllChanges(testRepository);
                    gitService.commitAndPush(testRepository, "Update the structure oracle file.", true, user);
                    return true;
                }
                catch (GitAPIException e) {
                    log.error("An exception occurred while pushing the structure oracle file to the test repository.", e);
                    return false;
                }
            }
        }
    }
}
