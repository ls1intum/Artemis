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

    private NotificationOption optionA;

    private NotificationOption optionB;

    /**
     * Prepares the common variables and data for testing
     */
    @BeforeEach
    public void initTestCase() {
        users = database.addUsers(2, 1, 1, 1);

        User student1 = users.get(0);
        users.set(0, student1);
        userRepository.save(student1);

        optionA = new NotificationOption(student1, true, false, "notification.lecture-notification.attachment-changes");
        optionB = new NotificationOption(student1, false, false, "notification.exercise-notification.exercise-open-for-practice");
    }

    /**
     * Cleans the test environment to make sure different test do not influence each other
     */
    @AfterEach
    public void tearDown() {
        database.resetDatabase();
        notificationOptionRepository.deleteAll();
    }

    /**
     * Tests the getNotificationOptionsForCurrentUser method
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testGetNotificationOptionsForCurrentUser() throws Exception {
        notificationOptionRepository.save(optionA);
        notificationOptionRepository.save(optionB);

        List<NotificationOption> notificationOptions = request.getList("/api/notification-settings/fetch-options", HttpStatus.OK, NotificationOption.class);

        // due to auto increment the ids change
        notificationOptions.get(0).setId(0);
        notificationOptions.get(1).setId(0);
        assertThat(notificationOptions).as("NotificationOption A with recipient equal to current user is returned").contains(optionA);
        assertThat(notificationOptions).as("NotificationOption B with recipient equal to current user is returned").contains(optionB);
    }

    /**
     * Tests the saveNotificationOptionsForCurrentUser method under normal (successful) conditions
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveNotificationOptionsForCurrentUser_OK() throws Exception {
        // NotificationOption[] newlyChangedOptionsToSave = new NotificationOption[] { optionA, optionB };
        NotificationOption[] newlyChangedOptionsToSave = { optionA, optionB };

        NotificationOption[] notificationOptionsResponse = request.postWithResponseBody("/api/notification-settings/save-options", newlyChangedOptionsToSave,
                NotificationOption[].class, HttpStatus.OK);

        // due to auto increment the ids change
        notificationOptionsResponse[0].setId(0);
        notificationOptionsResponse[1].setId(0);
        assertThat(notificationOptionsResponse).as("NotificationOption A succesfully saved").contains(optionA);
        assertThat(notificationOptionsResponse).as("NotificationOption B succesfully saved").contains(optionB);
    }

    /**
     * Tests the saveNotificationOptionsForCurrentUser method if a bad request occurs
     */
    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testSaveNotificationOptionsForCurrentUser_BAD_REQUEST() throws Exception {
        request.postWithResponseBody("/api/notification-settings/save-options", null, NotificationOption[].class, HttpStatus.BAD_REQUEST);
    }
}
