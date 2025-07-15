package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.communication.service.notifications.GroupNotificationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Profile(PROFILE_CORE)
@Service
public class ProgrammingTestCaseChangedUserNotificationService {

    private final GroupNotificationService groupNotificationService;

    private final WebsocketMessagingService websocketMessagingService;

    public ProgrammingTestCaseChangedUserNotificationService(GroupNotificationService groupNotificationService, WebsocketMessagingService websocketMessagingService) {
        this.groupNotificationService = groupNotificationService;
        this.websocketMessagingService = websocketMessagingService;
    }

    public static String getProgrammingExerciseTestCaseChangedTopic(Long programmingExerciseId) {
        return "/topic/programming-exercises/" + programmingExerciseId + "/test-cases-changed";
    }

    /**
     * Notifies editors and instructors about test case changes for the updated programming exercise
     *
     * @param testCasesChanged           whether tests have been changed or not
     * @param updatedProgrammingExercise the programming exercise for which tests have been changed
     */
    public void notifyUserAboutTestCaseChanged(boolean testCasesChanged, ProgrammingExercise updatedProgrammingExercise) {
        websocketMessagingService.sendMessage(getProgrammingExerciseTestCaseChangedTopic(updatedProgrammingExercise.getId()), testCasesChanged);
        // Send a notification to the client to inform the instructor about the test case update.
        if (testCasesChanged) {
            groupNotificationService.notifyEditorAndInstructorGroupsAboutChangedTestCasesForProgrammingExercise(updatedProgrammingExercise);
        }
        else {
            groupNotificationService.notifyEditorAndInstructorGroupAboutExerciseUpdate(updatedProgrammingExercise);
        }
    }

}
