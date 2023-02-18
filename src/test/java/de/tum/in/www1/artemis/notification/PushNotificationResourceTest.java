package de.tum.in.www1.artemis.notification;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceType;
import de.tum.in.www1.artemis.web.rest.push_notification.PushNotificationRegisterBody;
import de.tum.in.www1.artemis.web.rest.push_notification.PushNotificationUnregisterRequest;

public class PushNotificationResourceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Test
    @WithMockUser(roles = "USER")
    void testRegister() throws Exception {
        PushNotificationRegisterBody body = new PushNotificationRegisterBody("Test", PushNotificationDeviceType.FIREBASE);
        request.post("/api/push_notification/register", body, HttpStatus.OK);
    }

    @Test
    @WithMockUser(roles = "USER")
    void testUnregister() throws Exception {
        PushNotificationUnregisterRequest body = new PushNotificationUnregisterRequest("Test", PushNotificationDeviceType.FIREBASE);
        request.post("/api/push_notification/unregister", body, HttpStatus.OK);
    }
}
