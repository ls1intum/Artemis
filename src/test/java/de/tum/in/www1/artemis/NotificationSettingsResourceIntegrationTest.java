package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.NotificationOption;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.*;

public class NotificationSettingsResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationOptionRepository notificationOptionRepository;

    private List<User> users;

    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1, 1);

        User student1 = users.get(0);
        users.set(0, student1);
        userRepository.save(student1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        notificationOptionRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotificationOptions() throws Exception {
        User recipient = userRepository.getUser();

        NotificationOption notificationOption1 = new NotificationOption();
        notificationOption1.setId(1);
        notificationOption1.setUser(recipient);
        notificationOption1.setWebapp(false);
        notificationOption1.setEmail(false);
        notificationOption1.setOptionSpecifier("notification.exercise-notification.exercise-open-for-practice");
        notificationOptionRepository.save(notificationOption1);

        NotificationOption notificationOption2 = new NotificationOption();
        notificationOption2.setId(2);
        notificationOption2.setUser(recipient);
        notificationOption2.setWebapp(true);
        notificationOption2.setEmail(false);
        notificationOption2.setOptionSpecifier("notification.lecture-notification.attachment-changes");
        notificationOptionRepository.save(notificationOption2);

        // List<Notification> notifications = request.getList("/api/notifications", HttpStatus.OK, Notification.class);
        List<NotificationOption> notificationOptions = request.getList("/api/notification-settings/fetch-options", HttpStatus.OK, NotificationOption.class);
        // assertThat(notificationOptions).as("NotificationOptions with recipient equal to current user is returned").contains(notification1);
        // assertThat(notificationOptions).as("Notification with recipient not equal to current user is not returned").doesNotContain(notification2);

        assertThat(notificationOptions).as("NotificationOptions with recipient equal to current user is returned").contains(notificationOption1);

    }

}
