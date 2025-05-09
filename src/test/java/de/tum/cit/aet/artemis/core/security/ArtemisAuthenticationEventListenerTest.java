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

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ArtemisAuthenticationEventListenerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailSendingService mailSendingService;

    private ArtemisAuthenticationEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ArtemisAuthenticationEventListener(userRepository, mailSendingService);
    }

    @Test
    void shouldSendEmailToNonInternalUserOnSuccessfulAuthentication() throws EntityNotFoundException {
        String username = "testuser";
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        User nonInternalUser = new User();
        nonInternalUser.setLogin(username);
        nonInternalUser.setInternal(false);

        when(userRepository.getUserByLoginElseThrow(username)).thenReturn(nonInternalUser);

        listener.onApplicationEvent(event);

        verify(mailSendingService).buildAndSendAsync(eq(nonInternalUser), eq("email.notification.login.title"), eq("mail/notification/newLoginEmail"), any(Map.class));
    }

    @Test
    void shouldNotSendEmailToInternalUserOnSuccessfulAuthentication() throws EntityNotFoundException {
        String username = "internaluser";
        Authentication authentication = new UsernamePasswordAuthenticationToken(username, "password");
        AuthenticationSuccessEvent event = new AuthenticationSuccessEvent(authentication);

        User internalUser = new User();
        internalUser.setLogin(username);
        internalUser.setInternal(true);

        when(userRepository.getUserByLoginElseThrow(username)).thenReturn(internalUser);

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
