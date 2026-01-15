package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseTestCaseRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ProgrammingExerciseImportService {

    private final ProgrammingExerciseValidationService programmingExerciseValidationService;

    private final ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService;

    private final ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService;

    private final ProgrammingExerciseTaskService programmingExerciseTaskService;

    private final TemplateUpgradePolicyService templateUpgradePolicyService;

    private final ProgrammingExerciseImportBasicService programmingExerciseImportBasicService;

    private final ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository;

    public ProgrammingExerciseImportService(ProgrammingExerciseValidationService programmingExerciseValidationService,
            ProgrammingExerciseBuildPlanService programmingExerciseBuildPlanService, ProgrammingExerciseCreationScheduleService programmingExerciseCreationScheduleService,
            ProgrammingExerciseTaskService programmingExerciseTaskService, TemplateUpgradePolicyService templateUpgradePolicyService,
            ProgrammingExerciseImportBasicService programmingExerciseImportBasicService, ProgrammingExerciseTestCaseRepository programmingExerciseTestCaseRepository) {
        this.programmingExerciseValidationService = programmingExerciseValidationService;
        this.programmingExerciseBuildPlanService = programmingExerciseBuildPlanService;
        this.programmingExerciseCreationScheduleService = programmingExerciseCreationScheduleService;
        this.programmingExerciseTaskService = programmingExerciseTaskService;
        this.templateUpgradePolicyService = templateUpgradePolicyService;
        this.programmingExerciseImportBasicService = programmingExerciseImportBasicService;
        this.programmingExerciseTestCaseRepository = programmingExerciseTestCaseRepository;
    }

    /**
     * Method to import a programming exercise, including all base build plans (template, solution) and repositories (template, solution, test).
     * Referenced entities, s.a. the test cases or the hints will get cloned and assigned a new id.
     *
     * @param originalProgrammingExercise         the Programming Exercise which should be used as a blueprint
     * @param newProgrammingExercise              The new exercise already containing values which should not get copied, i.e. overwritten
     * @param updateTemplate                      if the template files should be updated
     * @param recreateBuildPlans                  if the build plans should be recreated
     * @param setTestCaseVisibilityToAfterDueDate if the test case visibility should be set to {@link Visibility#AFTER_DUE_DATE}
     * @return the imported programming exercise
     */
    public ProgrammingExercise importProgrammingExercise(ProgrammingExercise originalProgrammingExercise, ProgrammingExercise newProgrammingExercise, boolean updateTemplate,
            boolean recreateBuildPlans, boolean setTestCaseVisibilityToAfterDueDate) throws JsonProcessingException {
        // remove all non-alphanumeric characters from the short name. This gets already done in the client, but we do it again here to be sure
        newProgrammingExercise.setShortName(newProgrammingExercise.getShortName().replaceAll("[^a-zA-Z0-9]", ""));
        newProgrammingExercise.generateAndSetProjectKey();
        programmingExerciseValidationService.checkIfProjectExists(newProgrammingExercise);

        if (newProgrammingExercise.isExamExercise()) {
            // Disable feedback suggestions on exam exercises (currently not supported)
            newProgrammingExercise.setFeedbackSuggestionModule(null);
        }

        newProgrammingExercise = programmingExerciseImportBasicService.importProgrammingExerciseBasis(originalProgrammingExercise, newProgrammingExercise);
        programmingExerciseImportBasicService.importRepositories(originalProgrammingExercise, newProgrammingExercise);

        if (setTestCaseVisibilityToAfterDueDate) {
            Set<ProgrammingExerciseTestCase> testCases = this.programmingExerciseTestCaseRepository.findByExerciseId(newProgrammingExercise.getId());
            for (ProgrammingExerciseTestCase testCase : testCases) {
                testCase.setVisibility(Visibility.AFTER_DUE_DATE);
            }
            List<ProgrammingExerciseTestCase> updatedTestCases = programmingExerciseTestCaseRepository.saveAll(testCases);
            newProgrammingExercise.setTestCases(new HashSet<>(updatedTestCases));
        }

        // Update the template files
        if (updateTemplate) {
            TemplateUpgradeService upgradeService = templateUpgradePolicyService.getUpgradeService(newProgrammingExercise.getProgrammingLanguage());
            upgradeService.upgradeTemplate(newProgrammingExercise);
        }

        if (recreateBuildPlans) {
            // Create completely new build plans for the exercise
            programmingExerciseBuildPlanService.setupBuildPlansForNewExercise(newProgrammingExercise);
        }
        // Note: Build plan importing has been removed as part of stateless CI refactoring.

        programmingExerciseCreationScheduleService.scheduleOperations(newProgrammingExercise.getId());

        programmingExerciseTaskService.replaceTestIdsWithNames(newProgrammingExercise);
        return newProgrammingExercise;
    }

}
