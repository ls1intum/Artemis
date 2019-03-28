package de.tum.in.www1.artemis.service;

import com.google.gson.JsonObject;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.GroupNotification;
import de.tum.in.www1.artemis.domain.Notification;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.GroupNotificationType;
import de.tum.in.www1.artemis.repository.GroupNotificationRepository;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupNotificationService {
    private GroupNotificationRepository groupNotificationRepository;
    private final SimpMessageSendingOperations messagingTemplate;
    private UserService userService;

    public GroupNotificationService(
            GroupNotificationRepository groupNotificationRepository,
            SimpMessageSendingOperations messagingTemplate,
            UserService userService) {
        this.groupNotificationRepository = groupNotificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
    }

    public void notifyGroupAboutExerciseChange(Exercise exercise) {
        GroupNotification groupNotification = new GroupNotification();
        groupNotification.setCourse(exercise.getCourse());
        groupNotification.setType(GroupNotificationType.INSTRUCTOR);
        groupNotification.setNotificationDate(ZonedDateTime.now());
        groupNotification.setTitle("Exercise updated");
        groupNotification.setText("Exercise " + exercise.getTitle() + " got updated.");
        JsonObject target = new JsonObject();
        target.addProperty("id", exercise.getId());
        target.addProperty("entity", "exercises");
        target.addProperty("mainPage", "overview");
        groupNotification.setTarget(target.toString());
        groupNotification.setAuthor(userService.getUser());
        String topic =
                "/topic/course/"
                        + groupNotification.getCourse().getId()
                        + "/"
                        + groupNotification.getType()
                        + "/exerciseUpdated";
        saveAndSendGroupNotification(topic, groupNotification);
    }

    private void saveAndSendGroupNotification(String topic, GroupNotification groupNotification) {
        groupNotificationRepository.save(groupNotification);
        messagingTemplate.convertAndSend(topic, groupNotification);
    }

    public List<Notification> findAllNewNotificationsForCurrentUser(User currentUser) {
        List<String> userGroups = currentUser.getGroups();
        if (userGroups.size() == 0) {
            return new ArrayList<>();
        }
        return this.groupNotificationRepository.findAllNewNotificationsForCurrentUser(
                userGroups, currentUser.getLastNotificationRead());
    }
}
