package de.tum.cit.aet.artemis.core.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;

import de.tum.cit.aet.artemis.communication.domain.EmailNotificationType;
import de.tum.cit.aet.artemis.communication.service.EmailNotificationSettingService;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;

@ExtendWith(MockitoExtension.class)
class ArtemisAuthenticationEventListenerTest {

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private MailSendingService mailSendingService;

    @Mock
    private EmailNotificationSettingService emailNotificationSettingService;

    private ArtemisAuthenticationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ArtemisAuthenticationEventListener(userRepository, mailSendingService, emailNotificationSettingService);
    }

    @Test
    void shouldSendEmailToUserOnSuccessfulAuthentication() throws EntityNotFoundException {
        String username = "testuser";
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        User nonInternalUser = new User();
        nonInternalUser.setLogin(username);
        nonInternalUser.setInternal(false);

        when(userRepository.getUserByLoginElseThrow(username)).thenReturn(nonInternalUser);
        when(emailNotificationSettingService.isNotificationEnabled(nonInternalUser.getId(), EmailNotificationType.NEW_LOGIN)).thenReturn(true);

        listener.onApplicationEvent(event);

        verify(mailSendingService).buildAndSendAsync(eq(nonInternalUser), eq("email.notification.login.title"), eq("mail/notification/newLoginEmail"), any(Map.class));
    }

    @Test
    void shouldNotSendEmailWhenNotificationDisabled() throws EntityNotFoundException {
        String username = "testuser";
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        User nonInternalUser = new User();
        nonInternalUser.setLogin(username);
        nonInternalUser.setInternal(false);

        when(userRepository.getUserByLoginElseThrow(username)).thenReturn(nonInternalUser);
        when(emailNotificationSettingService.isNotificationEnabled(nonInternalUser.getId(), EmailNotificationType.NEW_LOGIN)).thenReturn(false);

        listener.onApplicationEvent(event);

        verify(mailSendingService, never()).buildAndSendAsync(any(User.class), anyString(), anyString(), any(Map.class));
    }

    @Test
    void shouldHandleUserNotFoundGracefully() throws EntityNotFoundException {
        String username = "nonexistentuser";
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        when(userRepository.getUserByLoginElseThrow(username)).thenThrow(new EntityNotFoundException("User not found"));

        listener.onApplicationEvent(event);

        verify(mailSendingService, never()).buildAndSendAsync(any(User.class), anyString(), anyString(), any(Map.class));
    }
}
