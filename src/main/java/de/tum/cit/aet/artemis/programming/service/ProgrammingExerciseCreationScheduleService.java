package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationScheduleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Service
@Lazy
@Profile(PROFILE_CORE)
public class ProgrammingExerciseCreationScheduleService {

    private final GroupNotificationScheduleService groupNotificationScheduleService;

    private final InstanceMessageSendService instanceMessageSendService;

    public ProgrammingExerciseCreationScheduleService(GroupNotificationScheduleService groupNotificationScheduleService, InstanceMessageSendService instanceMessageSendService) {
        this.groupNotificationScheduleService = groupNotificationScheduleService;
        this.instanceMessageSendService = instanceMessageSendService;
    }

    public void performScheduleOperationsAndCheckNotifications(ProgrammingExercise programmingExercise) {
        scheduleOperations(programmingExercise.getId());
        groupNotificationScheduleService.checkNotificationsForNewExerciseAsync(programmingExercise);
    }

    public void scheduleOperations(long programmingExerciseId) {
        instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExerciseId);
    }

    public void createNotificationsOnUpdate(ProgrammingExercise programmingExerciseBeforeUpdate, ProgrammingExercise savedProgrammingExercise, String notificationText) {
        groupNotificationScheduleService.checkAndCreateAppropriateNotificationsWhenUpdatingExercise(programmingExerciseBeforeUpdate, savedProgrammingExercise, notificationText);

    }

}
