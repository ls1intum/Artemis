package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationTriggerService;

@Service
@Profile(PROFILE_CORE)
@Lazy
public class ProgrammingExerciseTestCaseChangedService {

    private static final Logger log = LoggerFactory.getLogger(ProgrammingExerciseTestCaseChangedService.class);

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ResultRepository resultRepository;

    private final ProgrammingTestCaseChangedUserNotificationService programmingTestCaseChangedUserNotificationService;

    private final Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService;

    public ProgrammingExerciseTestCaseChangedService(ProgrammingExerciseRepository programmingExerciseRepository, ResultRepository resultRepository,
            ProgrammingTestCaseChangedUserNotificationService programmingTestCaseChangedUserNotificationService,
            Optional<ContinuousIntegrationTriggerService> continuousIntegrationTriggerService) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.resultRepository = resultRepository;
        this.programmingTestCaseChangedUserNotificationService = programmingTestCaseChangedUserNotificationService;
        this.continuousIntegrationTriggerService = continuousIntegrationTriggerService;
    }

    /**
     * Executes setTestCasesChanged with testCasesChanged = true, also triggers template and solution build.
     * This method should be used if the solution participation would otherwise not be built.
     *
     * @param programmingExerciseId ProgrammingExercise id
     * @throws EntityNotFoundException if there is no programming exercise for the given id.
     */
    public void setTestCasesChangedAndTriggerTestCaseUpdate(long programmingExerciseId) throws EntityNotFoundException {
        setTestCasesChanged(programmingExerciseId, true);
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(programmingExerciseId).orElseThrow();

        try {
            ContinuousIntegrationTriggerService ciTriggerService = continuousIntegrationTriggerService.orElseThrow();
            ciTriggerService.triggerBuild(programmingExercise.getSolutionParticipation());
            ciTriggerService.triggerBuild(programmingExercise.getTemplateParticipation());
        }
        catch (ContinuousIntegrationException ex) {
            log.error("Could not trigger build for solution repository after test case update for programming exercise with id {}", programmingExerciseId);
        }
    }

    /**
     * see the description below
     *
     * @param programmingExerciseId id of a ProgrammingExercise.
     * @param testCasesChanged      set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public void setTestCasesChanged(long programmingExerciseId, boolean testCasesChanged) throws EntityNotFoundException {
        var programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        setTestCasesChanged(programmingExercise, testCasesChanged);
    }

    /**
     * If testCasesChanged = true, this marks the programming exercise as dirty, meaning that its test cases were changed and the student submissions should be built & tested.
     * This method also sends out a notification to the client if testCasesChanged = true.
     * In case the testCaseChanged value is the same for the programming exercise or the programming exercise is not released or has no results, the method will return immediately.
     *
     * @param programmingExercise a ProgrammingExercise.
     * @param testCasesChanged    set to true to mark the programming exercise as dirty.
     * @throws EntityNotFoundException if the programming exercise does not exist.
     */
    public void setTestCasesChanged(ProgrammingExercise programmingExercise, boolean testCasesChanged) throws EntityNotFoundException {

        // If the flag testCasesChanged has not changed, we can stop the execution
        // Also, if the programming exercise has no results yet, there is no point in setting test cases changed to *true*.
        // It is only relevant when there are student submissions that should get an updated result.

        boolean resultsExist = resultRepository.existsByExerciseId(programmingExercise.getId());

        if (testCasesChanged == programmingExercise.getTestCasesChanged() || (!resultsExist && testCasesChanged)) {
            return;
        }
        programmingExercise.setTestCasesChanged(testCasesChanged);
        ProgrammingExercise updatedProgrammingExercise = programmingExerciseRepository.save(programmingExercise);
        // Send a websocket message about the new state to the client.
        programmingTestCaseChangedUserNotificationService.notifyUserAboutTestCaseChanged(testCasesChanged, updatedProgrammingExercise);
    }
}
