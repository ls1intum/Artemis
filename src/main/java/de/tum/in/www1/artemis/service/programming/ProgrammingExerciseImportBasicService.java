package de.tum.in.www1.artemis.service.programming;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.ExerciseMode;
import de.tum.in.www1.artemis.domain.enumeration.RepositoryType;
import de.tum.in.www1.artemis.domain.hestia.CodeHint;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseSolutionEntry;
import de.tum.in.www1.artemis.domain.hestia.ProgrammingExerciseTask;
import de.tum.in.www1.artemis.domain.submissionpolicy.SubmissionPolicy;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.hestia.ExerciseHintRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseSolutionEntryRepository;
import de.tum.in.www1.artemis.repository.hestia.ProgrammingExerciseTaskRepository;
import de.tum.in.www1.artemis.service.StaticCodeAnalysisService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.hestia.ExerciseHintService;

@Service
public class ProgrammingExerciseImportBasicService {

    private final ExerciseHintService exerciseHintService;

    private final ExerciseHintRepository exerciseHintRepository;

    private final Optional<VersionControlService> versionControlService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    private final StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final StaticCodeAnalysisService staticCodeAnalysisService;

    private final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    private final SubmissionPolicyRepository submissionPolicyRepository;

    private final ProgrammingExerciseTaskRepository programmingExerciseTaskRepository;

    private final ProgrammingExerciseSolutionEntryRepository solutionEntryRepository;

    public ProgrammingExerciseImportBasicService(ExerciseHintService exerciseHintService, ExerciseHintRepository exerciseHintRepository,
            Optional<VersionControlService> versionControlService, ProgrammingExerciseParticipationService programmingExerciseParticipationService,
            ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository, StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, ProgrammingExerciseService programmingExerciseService, StaticCodeAnalysisService staticCodeAnalysisService,
            AuxiliaryRepositoryRepository auxiliaryRepositoryRepository, SubmissionPolicyRepository submissionPolicyRepository,
            ProgrammingExerciseTaskRepository programmingExerciseTaskRepository, ProgrammingExerciseSolutionEntryRepository solutionEntryRepository) {
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
        this.solutionEntryRepository = solutionEntryRepository;
    }

    /**
     * Imports a programming exercise creating a new entity, copying all basic values and saving it in the database.
     * All basic include everything except for repositories, or build plans on a remote version control server, or
     * continuous integration server. <br>
     * There are however, a couple of things that will never get copied:
     * <ul>
     *     <li>The ID</li>
     *     <li>The template and solution participation</li>
     *     <li>The number of complaints, assessments and more feedback requests</li>
     *     <li>The tutor/student participations</li>
     *     <li>The questions asked by students</li>
     *     <li>The example submissions</li>
     * </ul>
     *
     * @param templateExercise The template exercise which should get imported
     * @param newExercise      The new exercise already containing values which should not get copied, i.e. overwritten
     * @return The newly created exercise
     */
    @Transactional // ok because we create many objects in a rather complex way and need a rollback in case of exceptions
    // IMPORTANT: the transactional context only works if you invoke this method from another class
    public ProgrammingExercise importProgrammingExerciseBasis(final ProgrammingExercise templateExercise, final ProgrammingExercise newExercise) {
        // Set values we don't want to copy to null
        setupExerciseForImport(newExercise);
        newExercise.setBranch(versionControlService.get().getDefaultBranchOfArtemis());

        // Note: same order as when creating an exercise
        programmingExerciseParticipationService.setupInitialTemplateParticipation(newExercise);
        programmingExerciseParticipationService.setupInitialSolutionParticipation(newExercise);
        setupTestRepository(newExercise);
        programmingExerciseService.initParticipations(newExercise);

        // Hints, tasks, test cases and static code analysis categories
        Map<Long, Long> newHintIdByOldId = exerciseHintService.copyExerciseHints(templateExercise, newExercise);
        programmingExerciseRepository.save(newExercise);
        Map<Long, Long> newTestCaseIdByOldId = importTestCases(templateExercise, newExercise);
        Map<Long, Long> newTaskIdByOldId = importTasks(templateExercise, newExercise, newTestCaseIdByOldId);
        updateTaskExerciseHintReferences(templateExercise, newExercise, newTaskIdByOldId, newHintIdByOldId);
        importSolutionEntries(templateExercise, newExercise, newTestCaseIdByOldId, newHintIdByOldId);

        // Copy or create SCA categories
        if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled() && Boolean.TRUE.equals(templateExercise.isStaticCodeAnalysisEnabled()))) {
            importStaticCodeAnalysisCategories(templateExercise, newExercise);
        }
        else if (Boolean.TRUE.equals(newExercise.isStaticCodeAnalysisEnabled()) && !Boolean.TRUE.equals(templateExercise.isStaticCodeAnalysisEnabled())) {
            staticCodeAnalysisService.createDefaultCategories(newExercise);
        }

        // An exam exercise can only be in individual mode
        if (newExercise.isExamExercise()) {
            newExercise.setMode(ExerciseMode.INDIVIDUAL);
            newExercise.setTeamAssignmentConfig(null);
        }

        importSubmissionPolicy(newExercise);

        // Re-adding auxiliary repositories
        List<AuxiliaryRepository> auxiliaryRepositoriesToBeImported = templateExercise.getAuxiliaryRepositories();

        for (AuxiliaryRepository auxiliaryRepository : auxiliaryRepositoriesToBeImported) {
            AuxiliaryRepository newAuxiliaryRepository = auxiliaryRepository.cloneObjectForNewExercise();
            auxiliaryRepositoryRepository.save(newAuxiliaryRepository);
            newExercise.addAuxiliaryRepository(newAuxiliaryRepository);
        }

        programmingExerciseRepository.save(newExercise);

        return newExercise;
    }

    /**
     * Sets up the test repository for a new exercise by setting the repository URL. This does not create the actual
     * repository on the version control server!
     *
     * @param newExercise the new exercises that should be created during import
     */
    private void setupTestRepository(ProgrammingExercise newExercise) {
        final var testRepoName = newExercise.generateRepositoryName(RepositoryType.TESTS);
        newExercise.setTestRepositoryUrl(versionControlService.get().getCloneRepositoryUrl(newExercise.getProjectKey(), testRepoName).toString());
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
        }).collect(Collectors.toList()));
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
        targetExercise.setStaticCodeAnalysisCategories(templateExercise.getStaticCodeAnalysisCategories().stream().map(originalCategory -> {
            var categoryCopy = new StaticCodeAnalysisCategory();
            categoryCopy.setName(originalCategory.getName());
            categoryCopy.setPenalty(originalCategory.getPenalty());
            categoryCopy.setMaxPenalty(originalCategory.getMaxPenalty());
            categoryCopy.setState(originalCategory.getState());
            categoryCopy.setProgrammingExercise(targetExercise);

            staticCodeAnalysisCategoryRepository.save(categoryCopy);
            return categoryCopy;
        }).collect(Collectors.toSet()));
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
        newExercise.setExerciseHints(null);
        newExercise.setTestCases(null);
        newExercise.setStaticCodeAnalysisCategories(null);
        newExercise.setAttachments(null);
        newExercise.setPlagiarismCases(null);
        newExercise.setNumberOfMoreFeedbackRequests(null);
        newExercise.setNumberOfComplaints(null);
        newExercise.setTotalNumberOfAssessments(null);
        newExercise.setTutorParticipations(null);
        newExercise.setExampleSubmissions(null);
        newExercise.setPosts(null);
        newExercise.setStudentParticipations(null);
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
