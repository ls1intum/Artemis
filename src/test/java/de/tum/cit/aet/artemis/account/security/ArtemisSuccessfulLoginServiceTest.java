package de.tum.cit.aet.artemis.account.security;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.jwt.AuthenticationMethod;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendEmailToUserWhenServiceMethodIsInvoked() throws EntityNotFoundException {
        String username = TEST_PREFIX + "student1";

        User user = userTestRepository.findOneByLogin(username).get();

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        artemisSuccessfulLoginService.sendLoginEmail(username, AuthenticationMethod.PASSWORD, null);
        await().atMost(5, SECONDS).untilAsserted(() -> verify(mailSendingService).buildAndSendSync(eq(MailRecipientDTO.from(user)), eq("email.notification.login.title"),
                eq("mail/notification/newLoginEmail"), anyMap()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendEmailToUserWhenLogingInWithEmail() throws EntityNotFoundException {
        String username = TEST_PREFIX + "student1";

        User user = userTestRepository.findOneByLogin(username).get();

        doNothing().when(javaMailSender).send(any(MimeMessage.class));
        artemisSuccessfulLoginService.sendLoginEmail(user.getEmail(), AuthenticationMethod.PASSWORD, null);
        await().atMost(5, SECONDS).untilAsserted(() -> verify(mailSendingService).buildAndSendSync(eq(MailRecipientDTO.from(user)), eq("email.notification.login.title"),
                eq("mail/notification/newLoginEmail"), anyMap()));
    }

    /**
     * The login mail ends in the shared {@code JavaMailSender} spy. If it were sent from a pool thread, it could still be on its way when the next test stubs or
     * resets that spy, which corrupts Mockito's state and fails that test in its {@code @BeforeEach}. Under the test profile it has to be sent by the thread that
     * triggers the login.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldSendTheLoginEmailOnTheCallingThread() {
        Thread testThread = Thread.currentThread();
        AtomicReference<Thread> sendingThread = new AtomicReference<>();
        doAnswer(invocation -> {
            sendingThread.set(Thread.currentThread());
            return null;
        }).when(javaMailSender).send(any(MimeMessage.class));

        artemisSuccessfulLoginService.sendLoginEmail(TEST_PREFIX + "student1", AuthenticationMethod.PASSWORD, null);

        await().atMost(5, SECONDS).untilAsserted(() -> assertThat(sendingThread.get()).isSameAs(testThread));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldHandleUserNotFoundGracefully() throws EntityNotFoundException {
        String username = "nonexistentuser";
        doNothing().when(javaMailSender).send(any(MimeMessage.class));

        artemisSuccessfulLoginService.sendLoginEmail(username, AuthenticationMethod.PASSWORD, null);

        await().atMost(5, SECONDS).untilAsserted(() -> verify(mailSendingService, never()).buildAndSendSync(any(MailRecipientDTO.class), anyString(), anyString(), anyMap()));
    }
}
