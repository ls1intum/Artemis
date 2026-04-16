package de.tum.cit.aet.artemis.core.security;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.core.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

@ExtendWith(MockitoExtension.class)
class ArtemisSuccessfulLoginServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "arsucloginst";

    @Autowired
    private ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 1, 2, 0, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void shouldSendEmailToUserWhenServiceMethodIsInvoked() throws EntityNotFoundException {
        String username = TEST_PREFIX + "student1";

        User user = userTestRepository.findOneByLogin(username).get();

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        artemisSuccessfulLoginService.sendLoginEmail(username, AuthenticationMethod.PASSWORD, null);
        await().atMost(5, SECONDS)
                .untilAsserted(() -> verify(mailSendingService).buildAndSendAsync(eq(user), eq("email.notification.login.title"), eq("mail/notification/newLoginEmail"), anyMap()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void shouldSendEmailToUserWhenLogingInWithEmail() throws EntityNotFoundException {
        String username = TEST_PREFIX + "student1";

        User user = userTestRepository.findOneByLogin(username).get();

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        artemisSuccessfulLoginService.sendLoginEmail(user.getEmail(), AuthenticationMethod.PASSWORD, null);
        await().atMost(5, SECONDS)
                .untilAsserted(() -> verify(mailSendingService).buildAndSendAsync(eq(user), eq("email.notification.login.title"), eq("mail/notification/newLoginEmail"), anyMap()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "INSTRUCTOR")
    void shouldHandleUserNotFoundGracefully() throws EntityNotFoundException {
        String username = "nonexistentuser";
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        artemisSuccessfulLoginService.sendLoginEmail(username, AuthenticationMethod.PASSWORD, null);

        await().atMost(5, SECONDS).untilAsserted(() -> verify(mailSendingService, never()).buildAndSendAsync(any(User.class), anyString(), anyString(), anyMap()));
    }
}
