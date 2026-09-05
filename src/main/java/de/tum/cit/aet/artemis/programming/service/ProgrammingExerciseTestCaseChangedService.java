package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.localci.service.ci.ContinuousIntegrationTriggerService;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

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
     * If testCasesChanged = true, this marks the programming exercise as dirty, meaning that its test cases were changed and the student submissions should be built & tested.
     * This method also sends out a notification to the client if testCasesChanged = true.
     * In case the testCaseChanged value is the same for the programming exercise or the programming exercise has no results, the method will return immediately.
     * <p>
     * The flag is flipped with a single guarded statement rather than by loading the exercise and saving it back. It is
     * one boolean, and reading the exercise for it fetched the whole exercise and the course it eagerly brings along,
     * then merged all of it back. Writing it directly also removes the window in which that merge could overwrite
     * another field of the exercise with the value it had when the exercise was read, which for a "build all" run is
     * the whole duration of the run.
     *
     * @param programmingExerciseId id of a ProgrammingExercise.
     * @param testCasesChanged      set to true to mark the programming exercise as dirty.
     */
    public void setTestCasesChanged(long programmingExerciseId, boolean testCasesChanged) {
        // Marking the exercise as dirty is only relevant when there are student submissions whose result should be
        // updated, so a request to set the flag is dropped when the exercise has no results at all.
        if (testCasesChanged && !resultRepository.existsByExerciseId(programmingExerciseId)) {
            return;
        }
        // The statement only touches the row when the flag differs, so its row count says whether anything changed and
        // the previous value never has to be read.
        if (programmingExerciseRepository.updateTestCasesChanged(programmingExerciseId, testCasesChanged) == 0) {
            return;
        }
        // Only the notification needs the exercise itself, and only when the flag really changed.
        var updatedProgrammingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExerciseId);
        // Send a websocket message about the new state to the client.
        programmingTestCaseChangedUserNotificationService.notifyUserAboutTestCaseChanged(testCasesChanged, updatedProgrammingExercise);
    }
}
