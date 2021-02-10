package de.tum.in.www1.artemis.service.messaging;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.ProgrammingExerciseService;
import de.tum.in.www1.artemis.service.TextExerciseService;
import de.tum.in.www1.artemis.service.scheduled.AtheneScheduleService;
import de.tum.in.www1.artemis.service.scheduled.ProgrammingExerciseScheduleService;

/**
 * This service is only available on a node with the 'scheduling' profile.
 * It receives messages from Hazelcast whenever another node sends a message to a specific topic and processes it on this node.
 */
@Service
@Profile("scheduling")
public class InstanceMessageReceiveService {

    private final Logger log = LoggerFactory.getLogger(InstanceMessageReceiveService.class);

    protected ProgrammingExerciseService programmingExerciseService;

    private ProgrammingExerciseScheduleService programmingExerciseScheduleService;

    protected TextExerciseService textExerciseService;

    private Optional<AtheneScheduleService> atheneScheduleService;

    public InstanceMessageReceiveService(ProgrammingExerciseService programmingExerciseService, ProgrammingExerciseScheduleService programmingExerciseScheduleService,
            TextExerciseService textExerciseService, Optional<AtheneScheduleService> atheneScheduleService, HazelcastInstance hazelcastInstance) {
        this.programmingExerciseService = programmingExerciseService;
        this.programmingExerciseScheduleService = programmingExerciseScheduleService;
        this.textExerciseService = textExerciseService;
        this.atheneScheduleService = atheneScheduleService;

        hazelcastInstance.<Long>getTopic("programming-exercise-schedule").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleProgrammingExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic("text-exercise-schedule").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processScheduleTextExercise((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic("text-exercise-schedule-cancel").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processTextExerciseScheduleCancel((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic("text-exercise-schedule-instant-clustering").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processTextExerciseInstantClustering((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic("programming-exercise-unlock-repositories").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processUnlockAllRepositories((message.getMessageObject()));
        });
        hazelcastInstance.<Long>getTopic("programming-exercise-lock-repositories").addMessageListener(message -> {
            SecurityUtils.setAuthorizationObject();
            processLockAllRepositories((message.getMessageObject()));
        });
    }

    public void processScheduleProgrammingExercise(Long exerciseId) {
        log.info("Received schedule update for programming exercise " + exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        programmingExerciseScheduleService.updateScheduling(programmingExercise);
    }

    public void processScheduleTextExercise(Long exerciseId) {
        log.info("Received schedule update for text exercise " + exerciseId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        atheneScheduleService.ifPresent(service -> service.scheduleExerciseForAtheneIfRequired(textExercise));
    }

    public void processTextExerciseScheduleCancel(Long exerciseId) {
        log.info("Received schedule cancel for text exercise " + exerciseId);
        atheneScheduleService.ifPresent(service -> service.cancelScheduledAthene(exerciseId));
    }

    public void processTextExerciseInstantClustering(Long exerciseId) {
        log.info("Received schedule instant clustering for text exercise " + exerciseId);
        TextExercise textExercise = textExerciseService.findOne(exerciseId);
        atheneScheduleService.ifPresent(service -> service.scheduleExerciseForInstantAthene(textExercise));
    }

    public void processUnlockAllRepositories(Long exerciseId) {
        log.info("Received unlock all repositories for programming exercise " + exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        // Run the runnable immediately so that the repositories are unlocked as fast as possible
        programmingExerciseScheduleService.unlockAllStudentRepositories(programmingExercise).run();
    }

    public void processLockAllRepositories(Long exerciseId) {
        log.info("Received lock all repositories for programming exercise " + exerciseId);
        ProgrammingExercise programmingExercise = programmingExerciseService.findWithTemplateParticipationAndSolutionParticipationById(exerciseId);
        // Run the runnable immediately so that the repositories are locked as fast as possible
        programmingExerciseScheduleService.lockAllStudentRepositories(programmingExercise).run();
    }
}
