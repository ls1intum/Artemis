package de.tum.in.www1.artemis.service;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Feedback;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.TestCaseVisibility;
import de.tum.in.www1.artemis.repository.CustomAuditEventRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.web.rest.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Service
public class ProgrammingExerciseTestCaseService {

    private final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseService.class);

    private final ProgrammingExerciseTestCaseRepository testCaseRepository;

    private final ProgrammingExerciseService programmingExerciseService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final CustomAuditEventRepository auditEventRepository;

    public ProgrammingExerciseTestCaseService(ProgrammingExerciseTestCaseRepository testCaseRepository, ProgrammingExerciseService programmingExerciseService,
            ProgrammingSubmissionService programmingSubmissionService, CustomAuditEventRepository auditEventRepository) {
        this.testCaseRepository = testCaseRepository;
        this.programmingExerciseService = programmingExerciseService;
        this.programmingSubmissionService = programmingSubmissionService;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Returns all test cases for a programming exercise.
     *
     * @param exerciseId of a programming exercise.
     * @return test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findByExerciseId(Long exerciseId) {
        return this.testCaseRepository.findByExerciseId(exerciseId);
    }

    /**
     * Returns all active test cases for a programming exercise. Only active test cases are evaluated on build runs.
     *
     * @param exerciseId of a programming exercise.
     * @return active test cases of a programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> findActiveByExerciseId(Long exerciseId) {
        return this.testCaseRepository.findByExerciseIdAndActive(exerciseId, true);
    }

    /**
     * Update the updatable attributes of the provided test case dtos. Returns an entry in the set for each test case that could be updated.
     *
     * @param exerciseId            of exercise the test cases belong to.
     * @param testCaseProgrammingExerciseTestCaseDTOS of the test cases to update the weights and afterDueDate flag of.
     * @return the updated test cases.
     * @throws EntityNotFoundException if the programming exercise could not be found.
     * @throws IllegalAccessException if the retriever does not have the permissions to fetch information related to the programming exercise.
     */
    public Set<ProgrammingExerciseTestCase> update(Long exerciseId, Set<ProgrammingExerciseTestCaseDTO> testCaseProgrammingExerciseTestCaseDTOS)
            throws EntityNotFoundException, IllegalAccessException {
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTestCasesById(exerciseId);
        Set<ProgrammingExerciseTestCase> existingTestCases = programmingExercise.getTestCases();

        Set<ProgrammingExerciseTestCase> updatedTests = new HashSet<>();
        for (ProgrammingExerciseTestCaseDTO programmingExerciseTestCaseDTO : testCaseProgrammingExerciseTestCaseDTOS) {
            Optional<ProgrammingExerciseTestCase> matchingTestCaseOpt = existingTestCases.stream()
                    .filter(testCase -> testCase.getId().equals(programmingExerciseTestCaseDTO.getId())).findFirst();
            if (matchingTestCaseOpt.isEmpty()) {
                continue;
            }

            ProgrammingExerciseTestCase matchingTestCase = matchingTestCaseOpt.get();
            matchingTestCase.setWeight(programmingExerciseTestCaseDTO.getWeight());
            matchingTestCase.setVisibility(programmingExerciseTestCaseDTO.getVisibility());
            matchingTestCase.setBonusMultiplier(programmingExerciseTestCaseDTO.getBonusMultiplier());
            matchingTestCase.setBonusPoints(programmingExerciseTestCaseDTO.getBonusPoints());
            updatedTests.add(matchingTestCase);
        }

        // Make sure that at least one test has a weight so that students can still achieve 100% score
        var testWeightsSum = existingTestCases.stream().mapToDouble(testCase -> Optional.ofNullable(testCase.getWeight()).orElse(0.0)).sum();
        if (testWeightsSum <= 0) {
            throw new BadRequestAlertException("The sum of all test case weights is 0 or below.", "TestCaseGrading", "weightSumError");
        }

        testCaseRepository.saveAll(updatedTests);
        // At least one test was updated with a new weight or runAfterDueDate flag. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTests;
    }

    /**
     * Reset the weights of all test cases to 1.
     *
     * @param exerciseId to find exercise test cases
     * @return test cases that have been reset
     */
    public List<ProgrammingExerciseTestCase> reset(Long exerciseId) {
        Set<ProgrammingExerciseTestCase> testCases = this.testCaseRepository.findByExerciseId(exerciseId);
        for (ProgrammingExerciseTestCase testCase : testCases) {
            testCase.setWeight(1.0);
            testCase.setBonusMultiplier(1.0);
            testCase.setBonusPoints(0.0);
        }
        List<ProgrammingExerciseTestCase> updatedTestCases = testCaseRepository.saveAll(testCases);
        // The tests' weights were updated. We use this flag to inform the instructor about outdated student results.
        programmingSubmissionService.setTestCasesChangedAndTriggerTestCaseUpdate(exerciseId);
        return updatedTestCases;
    }

    /**
     * From a list of build run feedback, extract all test cases. If an already stored test case is not found anymore in the build result, it will not be deleted, but set inactive.
     * This way old test cases are not lost, some interfaces in the client might need this information to e.g. show warnings.
     *
     * @param feedbacks list of build log output.
     * @param exercise  programming exercise.
     * @return Returns true if the test cases have changed, false if they haven't.
     */
    public boolean generateTestCasesFromFeedbacks(List<Feedback> feedbacks, ProgrammingExercise exercise) {
        Set<ProgrammingExerciseTestCase> existingTestCases = testCaseRepository.findByExerciseId(exercise.getId());
        // Do not generate test cases for static code analysis feedback
        Set<ProgrammingExerciseTestCase> testCasesFromFeedbacks = feedbacks.stream().filter(feedback -> !feedback.isStaticCodeAnalysisFeedback())
                // we use default values for weight, bonus multiplier and bonus points
                .map(feedback -> new ProgrammingExerciseTestCase().testName(feedback.getText()).weight(1.0).bonusMultiplier(1.0).bonusPoints(0.0).exercise(exercise).active(true)
                        .visibility(TestCaseVisibility.ALWAYS))
                .collect(Collectors.toSet());
        // Get test cases that are not already in database - those will be added as new entries.
        Set<ProgrammingExerciseTestCase> newTestCases = testCasesFromFeedbacks.stream().filter(testCase -> existingTestCases.stream().noneMatch(testCase::isSameTestCase))
                .collect(Collectors.toSet());
        // Get test cases which activate state flag changed.
        Set<ProgrammingExerciseTestCase> testCasesWithUpdatedActivation = existingTestCases.stream().filter(existing -> {
            Optional<ProgrammingExerciseTestCase> matchingText = testCasesFromFeedbacks.stream().filter(existing::isSameTestCase).findFirst();
            // Either the test case was active and is not part of the feedback anymore OR was not active before and is now part of the feedback again.
            return matchingText.isEmpty() && existing.isActive() || matchingText.isPresent() && matchingText.get().isActive() && !existing.isActive();
        }).map(existing -> existing.clone().active(!existing.isActive())).collect(Collectors.toSet());

        Set<ProgrammingExerciseTestCase> testCasesToSave = new HashSet<>();
        testCasesToSave.addAll(newTestCases);
        testCasesToSave.addAll(testCasesWithUpdatedActivation);

        if (testCasesToSave.size() > 0) {
            testCaseRepository.saveAll(testCasesToSave);
            return true;
        }
        return false;
    }

    public void logTestCaseReset(User user, ProgrammingExercise exercise, Course course) {
        var auditEvent = new AuditEvent(user.getLogin(), Constants.RESET_GRADING, "exercise=" + exercise.getTitle(), "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User " + user.getLogin() + " requested to reset the grading configuration for exercise {} with id {}", exercise.getTitle(), exercise.getId());
    }

}
