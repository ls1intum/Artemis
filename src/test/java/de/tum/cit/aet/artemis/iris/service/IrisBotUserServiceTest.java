package de.tum.cit.aet.artemis.iris.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ConversationService;

@ExtendWith(MockitoExtension.class)
class IrisBotUserServiceTest {

    private static final String IRIS_BOT_EMAIL = "iris-bot@localhost";

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private ConversationService conversationService;

    private IrisBotUserService irisBotUserService;

    @BeforeEach
    void setUp() {
        irisBotUserService = new IrisBotUserService(userRepository, passwordService, channelRepository, conversationService);
    }

    @Test
    void ensureIrisBotUserExists_doesNotReuseAnExistingEmail() {
        when(userRepository.findOneWithAuthoritiesByLogin(User.IRIS_BOT_LOGIN)).thenReturn(Optional.empty());
        when(userRepository.existsByEmailIgnoreCase(IRIS_BOT_EMAIL)).thenReturn(true);
        when(passwordService.hashPassword(any())).thenReturn("hashed-password");

        irisBotUserService.ensureIrisBotUserExists();

        var savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        assertThat(savedUserCaptor.getValue().getEmail()).isNull();
    }
}
