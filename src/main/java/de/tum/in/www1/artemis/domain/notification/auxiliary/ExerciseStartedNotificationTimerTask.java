package de.tum.in.www1.artemis.domain.notification.auxiliary;

import java.util.TimerTask;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.service.GroupNotificationService;

/**
 * Auxiliary class used for properly timing the sending of notifications based on created/started exercises
 */
public class ExerciseStartedNotificationTimerTask extends TimerTask {

    private Exercise exercise;

    private User author;

    private GroupNotificationService groupNotificationService;

    public ExerciseStartedNotificationTimerTask(Exercise exercise, User author, GroupNotificationService groupNotificationService) {
        this.exercise = exercise;
        this.author = author;
        this.groupNotificationService = groupNotificationService;
    }

    @Override
    public void run() {
        groupNotificationService.notifyStudentAndTutorGroupAboutStartedExercise(exercise, author);
    }
}
