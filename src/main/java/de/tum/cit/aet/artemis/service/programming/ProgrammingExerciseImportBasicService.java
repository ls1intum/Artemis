package de.tum.cit.aet.artemis.service.programming;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.domain.StaticCodeAnalysisCategory;
import de.tum.cit.aet.artemis.domain.enumeration.ExerciseMode;
import de.tum.cit.aet.artemis.domain.enumeration.RepositoryType;
import de.tum.cit.aet.artemis.domain.hestia.CodeHint;
import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.cit.aet.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.cit.aet.artemis.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.cit.aet.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.cit.aet.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.cit.aet.artemis.service.StaticCodeAnalysisService;
import de.tum.cit.aet.artemis.service.connectors.vcs.VersionControlService;
import de.tum.cit.aet.artemis.service.hestia.ExerciseHintService;
import de.tum.cit.aet.artemis.service.hestia.ProgrammingExerciseTaskService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingExerciseImportBasicService {

    private final ExerciseHintService exerciseHintService;

    private final ExerciseHintRepository exerciseHintRepository;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    private final ChannelService channelService;

    public ProgrammingExerciseImportBasicService(ExerciseHintService exerciseHintService, ExerciseHintRepository exerciseHintRepository,
            Optional<VersionControlService> versionControlService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseService programmingExerciseService, StaticCodeAnalysisService staticCodeAnalysisService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseTaskService programmingExerciseTaskService,
            ProgrammingExerciseSolutionEntryRepository solutionEntryRepository, ChannelService channelService,
            ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository) {
        this.exerciseHintService = exerciseHintService;
        this.exerciseHintRepository = exerciseHintRepository;
        this.versionControlService = versionControlService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
        this.staticCodeAnalysisCategoryRepository = staticCodeAnalysisCategoryRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.staticCodeAnalysisService = staticCodeAnalysisService;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
        this.submissionPolicyRepository = submissionPolicyRepository;
        this.programmingExerciseTaskRepository = programmingExerciseTaskRepository;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.solutionEntryRepository = solutionEntryRepository;
        this.channelService = channelService;
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
    }

    /**
     * Imports a programming exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except for repositories, or build plans on a remote version control server, or
     * continuous integration server. <br>
     * There are however, a couple of things that will never get copied:
     * <ul>
     * <li>The ID</li>
     * <li>The template and solution participation</li>
     * <li>The number of complaints, assessments and more feedback requests</li>
     * <li>The tutor/student participations</li>
     * <li>The questions asked by students</li>
     * <li>The example submissions</li>
     * </ul>
     *
     * @param originalProgrammingExercise The template exercise which should get imported
     * @param newProgrammingExercise      The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional // TODO: apply the transaction on a smaller scope
    // IMPORTANT: the transactional context only works if you invoke this method from another class
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise originalProgrammingExercise, final ProgrammingExercise newProgrammingExercise) {
        prepareBasicExerciseInformation(originalProgrammingExercise, newProgrammingExercise);

        // Note: same order as when creating an exercise
        programmingExerciseParticipationService.setupInitialTemplateParticipation(newProgrammingExercise);
        programmingExerciseParticipationService.setupInitialSolutionParticipation(newProgrammingExercise);
        setupTestRepository(newProgrammingExercise);
        programmingExerciseService.initParticipations(newProgrammingExercise);

        newProgrammingExercise.getBuildConfig().setBranch(versionControlService.orElseThrow().getDefaultBranchOfArtemis());
        if (newProgrammingExercise.getBuildConfig().getBuildPlanConfiguration() == null) {
            // this means the user did not override the build plan config when importing the exercise and want to reuse it from the existing exercise
            newProgrammingExercise.getBuildConfig().setBuildPlanConfiguration(originalProgrammingExercise.getBuildConfig().getBuildPlanConfiguration());
        }

        // Hints, tasks, test cases and static code analysis categories
        final Map<Long, Long> newHintIdByOldId = exerciseHintService.copyExerciseHints(originalProgrammingExercise, newProgrammingExercise);

        newProgrammingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(newProgrammingExercise.getBuildConfig()));
        final ProgrammingExercise importedExercise = programmingExerciseRepository.save(newProgrammingExercise);

        final Map<Long, Long> newTestCaseIdByOldId = importTestCases(originalProgrammingExercise, importedExercise);
        final Map<Long, Long> newTaskIdByOldId = importTasks(originalProgrammingExercise, importedExercise, newTestCaseIdByOldId);
        updateTaskExerciseHintReferences(originalProgrammingExercise, importedExercise, newTaskIdByOldId, newHintIdByOldId);

        // Set up new exercise submission policy before the solution entries are imported
        importSubmissionPolicy(importedExercise);
        // Having the submission policy in place prevents errors
        importSolutionEntries(originalProgrammingExercise, importedExercise, newTestCaseIdByOldId, newHintIdByOldId);

        // Use the template problem statement (with ids) as a new basis (You cannot edit the problem statement while importing)
        // Then replace the old test ids by the newly created ones.
        importedExercise.setProblemStatement(originalProgrammingExercise.getProblemStatement());
        programmingExerciseTaskService.updateTestIds(importedExercise, newTestCaseIdByOldId);

        // Copy or create SCA categories
        if (Boolean.TRUE.equals(importedExercise.isStaticCodeAnalysisEnabled() && Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled()))) {
            importStaticCodeAnalysisCategories(originalProgrammingExercise, importedExercise);
        }
        else if (Boolean.TRUE.equals(importedExercise.isStaticCodeAnalysisEnabled()) && !Boolean.TRUE.equals(originalProgrammingExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(importedExercise);
        }

        // An exam exercise can only be in individual mode
        if (importedExercise.isExamExercise()) {
            importedExercise.setMode(ExerciseMode.INDIVIDUAL);
            importedExercise.setTeamAssignmentConfig(null);
        }

        // Re-adding auxiliary repositories
        final List<AuxiliaryRepository> auxiliaryRepositoriesToBeImported = originalProgrammingExercise.getAuxiliaryRepositories();

        for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositoriesToBeImported) {
            AuxiliaryRepository newAuxiliaryRepository = auxiliaryRepository.cloneObjectForNewExercise();
            newAuxiliaryRepository = auxiliaryRepositoryRepository.save(newAuxiliaryRepository);
            importedExercise.addAuxiliaryRepository(newAuxiliaryRepository);
        }

        ProgrammingExercise savedImportedExercise = programmingExerciseRepository.save(importedExercise);

        channelService.createExerciseChannel(savedImportedExercise, Optional.ofNullable(newProgrammingExercise.getChannelName()));

        return savedImportedExercise;
    }

    /**
     * Prepares information directly stored in the exercise for the copy process.
     * <p>
     * Replaces attributes in the new exercise that should not be copied from the previous one.
     *
     * @param originalProgrammingExercise Some exercise the information is copied from.
     * @param newProgrammingExercise      The exercise that is prepared.
     */
    private void prepareBasicExerciseInformation(final ProgrammingExercise originalProgrammingExercise, final ProgrammingExercise newProgrammingExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newProgrammingExercise);
        setupBuildConfig(newProgrammingExercise, originalProgrammingExercise);

        if (originalProgrammingExercise.getBuildConfig().hasBuildPlanAccessSecretSet()) {
            newProgrammingExercise.getBuildConfig().generateAndSetBuildPlanAccessSecret();
        }
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URI. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupTestRepository(ProgrammingExercise newExercise) {
        final var testRepoName = newExercise.generateRepositoryName(RepositoryType.TESTS);
        newExercise.setTestRepositoryUri(versionControlService.orElseThrow().getCloneRepositoryUri(newExercise.getProjectKey(), testRepoName).toString());
    }

    private void setupBuildConfig(ProgrammingExercise newExercise, ProgrammingExercise originalExercise) {
        if (newExercise.getBuildConfig() != null) {
            var buildConfig = newExercise.getBuildConfig();
            buildConfig.setId(null);
            buildConfig.setProgrammingExercise(null);
            newExercise.setBuildConfig(buildConfig);
        }
        else if (originalExercise.getBuildConfig() != null) {
            var buildConfig = new ProgrammingExerciseBuildConfig(originalExercise.getBuildConfig());
            newExercise.setBuildConfig(buildConfig);
        }
        else {
            newExercise.setBuildConfig(new ProgrammingExerciseBuildConfig());
        }
    }

    /**
     * Persists the submission policy of the new exercise. We ensure that the submission policy does not
     * have any id or programming exercise set.
     *
     * @param newExercise containing the submission policy to persist
     */
    private void importSubmissionPolicy(ProgrammingExercise newExercise) {
        if (newExercise.getSubmissionPolicy() != null) {
            SubmissionPolicy newSubmissionPolicy = newExercise.getSubmissionPolicy();
            newSubmissionPolicy.setId(null);
            newSubmissionPolicy.setProgrammingExercise(null);
            newExercise.setSubmissionPolicy(submissionPolicyRepository.save(newSubmissionPolicy));
        }
    }

    /**
     * Copied test cases from one exercise to another. The test cases will get new IDs, thus being saved as a new entity.
     * The remaining contents stay the same, especially the weights.
     *
     * @param templateExercise The template exercise which test cases should get copied
     * @param targetExercise   The new exercise to which all test cases should get copied to
     * @return A map with the old test case id as a key and the new test case id as value
     */
    private Map<Long, Long> importTestCases(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        Map<Long, Long> newIdByOldId = new HashMap<>();
        targetExercise.setTestCases(templateExercise.getTestCases().stream().map(testCase -> {
            final var copy = new ProgrammingExerciseTestCase();

            // Copy everything except for the referenced exercise
            copy.setActive(testCase.isActive());
            copy.setVisibility(testCase.getVisibility());
            copy.setTestName(testCase.getTestName());
            copy.setWeight(testCase.getWeight());
            copy.setBonusMultiplier(testCase.getBonusMultiplier());
            copy.setBonusPoints(testCase.getBonusPoints());
            copy.setExercise(targetExercise);
            copy.setType(testCase.getType());
            programmingExerciseTestCaseRepository.save(copy);
            newIdByOldId.put(testCase.getId(), copy.getId());
            return copy;
        }).collect(Collectors.toSet()));

        return newIdByOldId;
    }

    /**
     * Copies tasks from one exercise to another. Because the tasks from the template exercise references its test cases, the
     * references between tasks and test cases also need to be changed.
     *
     * @param templateExercise     The template exercise which tasks should be copied
     * @param targetExercise       The new exercise to which all tasks should get copied to
     * @param newTestCaseIdByOldId A map with the old test case id as a key and the new test case id as a value
     * @return A map with the old task id as a key and the new task id as value
     */
    private Map<Long, Long> importTasks(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise, Map<Long, Long> newTestCaseIdByOldId) {
        Map<Long, Long> newIdByOldId = new HashMap<>();
        targetExercise.setTasks(templateExercise.getTasks().stream().map(task -> {
            final var copy = new ProgrammingExerciseTask();

            // copy everything except for the referenced exercise
            copy.setTaskName(task.getTaskName());
            // change reference to newly imported test cases from the target exercise
            copy.setTestCases(task.getTestCases().stream().map(testCase -> {
                Long oldTestCaseId = testCase.getId();
                Long newTestCaseId = newTestCaseIdByOldId.get(oldTestCaseId);
                return targetExercise.getTestCases().stream().filter(newTestCase -> Objects.equals(newTestCaseId, newTestCase.getId())).findFirst().orElseThrow();
            }).collect(Collectors.toSet()));
            copy.setExercise(targetExercise);
            programmingExerciseTaskRepository.save(copy);
            newIdByOldId.put(task.getId(), copy.getId());
            return copy;
        }).collect(Collectors.toCollection(ArrayList::new)));
        return newIdByOldId;
    }

    /**
     * Copies static code analysis categories from one exercise to another by creating new entities and copying the
     * appropriate fields.
     *
     * @param templateExercise with static code analysis categories which should get copied
     * @param targetExercise   for which static code analysis categories will be copied
     */
    private void importStaticCodeAnalysisCategories(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise) {
        if (targetExercise.getStaticCodeAnalysisCategories() == null) {
            targetExercise.setStaticCodeAnalysisCategories(new HashSet<>());
        }

        templateExercise.getStaticCodeAnalysisCategories().forEach(originalCategory -> {
            final var categoryCopy = new StaticCodeAnalysisCategory();
            categoryCopy.setName(originalCategory.getName());
            categoryCopy.setPenalty(originalCategory.getPenalty());
            categoryCopy.setMaxPenalty(originalCategory.getMaxPenalty());
            categoryCopy.setState(originalCategory.getState());
            categoryCopy.setProgrammingExercise(targetExercise);

            final var savedCopy = staticCodeAnalysisCategoryRepository.save(categoryCopy);
            targetExercise.addStaticCodeAnalysisCategory(savedCopy);
        });
    }

    /**
     * Sets up a new exercise for importing it by setting all values, that should either never get imported, or
     * for which we should create new entities (e.g. test cases) to null. This ensures that we do not copy
     * anything by accident.
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupExerciseForImport(ProgrammingExercise newExercise) {
        newExercise.setId(null);
        newExercise.setExampleSolutionPublicationDate(null);
        newExercise.setTemplateParticipation(null);
        newExercise.setSolutionParticipation(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setTotalNumberOfAssessments(null);

        newExercise.disconnectRelatedEntities();

        // copy the grading instructions to avoid issues with references to the original exercise
        newExercise.setGradingCriteria(newExercise.copyGradingCriteria(new HashMap<>()));

        // only copy the config for team programming exercise in courses
        if (newExercise.getMode() == ExerciseMode.TEAM && newExercise.isCourseExercise()) {
            newExercise.setTeamAssignmentConfig(newExercise.getTeamAssignmentConfig().copyTeamAssignmentConfig());
        }
        // We have to rebuild the auxiliary repositories
        newExercise.setAuxiliaryRepositories(new ArrayList<>());

        if (newExercise.isTeamMode()) {
            newExercise.getTeamAssignmentConfig().setId(null);
        }

        if (newExercise.isCourseExercise() && newExercise.getPlagiarismDetectionConfig() != null) {
            newExercise.getPlagiarismDetectionConfig().setId(null);
        }
        else if (newExercise.isCourseExercise() && newExercise.getPlagiarismDetectionConfig() == null) {
            newExercise.setPlagiarismDetectionConfig(PlagiarismDetectionConfig.createDefault());
        }
        else {
            newExercise.setPlagiarismDetectionConfig(null);
        }
    }

    /**
     * Updates the newly imported exercise hints to reference the newly imported tasks they belong to.
     *
     * @param templateExercise The template exercise which tasks should be copied
     * @param targetExercise   The new exercise to which all tasks should get copied to
     * @param newTaskIdByOldId A map with the old task id as a key and the new task id as a value
     * @param newHintIdByOldId A map with the old hint id as a key and the new hint id as a value
     */
    private void updateTaskExerciseHintReferences(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise, Map<Long, Long> newTaskIdByOldId,
            Map<Long, Long> newHintIdByOldId) {
        templateExercise.getExerciseHints().forEach(templateExerciseHint -> {
            var templateTask = templateExerciseHint.getProgrammingExerciseTask();
            if (templateTask == null) {
                return;
            }
            var targetTask = targetExercise.getTasks().stream().filter(newTask -> Objects.equals(newTask.getId(), newTaskIdByOldId.get(templateTask.getId()))).findAny()
                    .orElseThrow();
            var targetExerciseHint = targetExercise.getExerciseHints().stream()
                    .filter(newHint -> Objects.equals(newHint.getId(), newHintIdByOldId.get(templateExerciseHint.getId()))).findAny().orElseThrow();

            targetExerciseHint.setProgrammingExerciseTask(targetTask);
            exerciseHintRepository.save(targetExerciseHint);
            targetTask.getExerciseHints().add(targetExerciseHint);
        });
    }

    /**
     * Copies solution entries from one exercise to another. Because the solution entries from the template exercise
     * references its test cases and code hint, the references between them also need to be changed.
     *
     * @param templateExercise     The template exercise which tasks should be copied
     * @param targetExercise       The new exercise to which all tasks should get copied to
     * @param newTestCaseIdByOldId A map with the old test case id as a key and the new test case id as a value
     * @param newHintIdByOldId     A map with the old hint id as a key and the new hint id as a value
     */
    private void importSolutionEntries(final ProgrammingExercise templateExercise, final ProgrammingExercise targetExercise, Map<Long, Long> newTestCaseIdByOldId,
            Map<Long, Long> newHintIdByOldId) {
        templateExercise.getTestCases().forEach(testCase -> {
            var newSolutionEntries = solutionEntryRepository.findByTestCaseIdWithCodeHint(testCase.getId()).stream().map(solutionEntry -> {
                Long newTestCaseId = newTestCaseIdByOldId.get(testCase.getId());
                var targetTestCase = targetExercise.getTestCases().stream().filter(newTestCase -> Objects.equals(newTestCaseId, newTestCase.getId())).findFirst().orElseThrow();

                CodeHint codeHint = null;
                if (solutionEntry.getCodeHint() != null) {
                    Long newHintId = newHintIdByOldId.get(solutionEntry.getCodeHint().getId());
                    codeHint = (CodeHint) targetExercise.getExerciseHints().stream().filter(newHint -> Objects.equals(newHintId, newHint.getId())).findFirst().orElseThrow();
                }
                var copy = new ProgrammingExerciseSolutionEntry();
                copy.setCode(solutionEntry.getCode());
                copy.setPreviousCode(solutionEntry.getPreviousCode());
                copy.setLine(solutionEntry.getLine());
                copy.setPreviousLine(solutionEntry.getPreviousLine());
                copy.setTestCase(targetTestCase);
                targetTestCase.getSolutionEntries().add(copy);
                copy.setCodeHint(codeHint);
                if (codeHint != null) {
                    codeHint.getSolutionEntries().add(copy);
                }
                return copy;
            }).collect(Collectors.toSet());
            solutionEntryRepository.saveAll(newSolutionEntries);
        });
    }
}
