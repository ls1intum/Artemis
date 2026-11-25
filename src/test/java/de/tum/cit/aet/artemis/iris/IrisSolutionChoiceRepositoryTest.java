package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import jakarta.ws.rs.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import de.tum.cit.aet.artemis.iris.dao.IrisSessionContextDAO;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisSolutionChoice;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisSolutionChoiceDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSolutionChoiceRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisSolutionOfferDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionWriteRepositoryImpl;

@ExtendWith(MockitoExtension.class)
class IrisSolutionChoiceRepositoryTest {

    @Mock
    private IrisSessionRepository sessions;

    @Mock
    private IrisMessageRepository messages;

    @Mock
    private ObjectProvider<IrisSessionRepository> provider;

    private IrisSessionWriteRepositoryImpl repository;

    private IrisMessage offer;

    @BeforeEach
    void setup() {
        when(provider.getObject()).thenReturn(sessions);
        when(sessions.findByIdWithWriteLockElseThrow(1L)).thenReturn(new IrisChatSession());
        lenient().when(sessions.findContextById(1L)).thenReturn(Optional.of(new IrisSessionContextDAO(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, 42L, 7L)));
        offer = new IrisMessage();
        offer.setId(10L);
        offer.addContent(IrisJsonMessageContent.of(new IrisSolutionOfferDTO(IrisSolutionOfferDTO.TYPE, IrisChatMode.PROGRAMMING_EXERCISE_CHAT, 42L)));
        lenient().when(messages.findByIdElseThrow(10L)).thenReturn(offer);
        repository = new IrisSessionWriteRepositoryImpl(provider, messages);
    }

    @Test
    void consumesOfferAndPersistsChoiceWithTheUserMessage() {
        when(messages.findLatestVisibleMessageId(1L)).thenReturn(Optional.of(10L));
        when(messages.saveAndFlush(any())).thenAnswer(call -> {
            IrisMessage message = call.getArgument(0);
            message.setId(11L);
            return message;
        });
        when(messages.setListIndex(11L, 0)).thenReturn(1);
        var userMessage = new IrisMessage();
        userMessage.addContent(new IrisTextMessageContent("Show me the complete solution"));
        var saved = repository.appendSolutionChoice(1L, userMessage, new IrisSolutionChoiceRequestDTO(10L, IrisSolutionChoice.SHOW_SOLUTION));
        assertThat(saved.getSender()).isEqualTo(IrisMessageSender.USER);
        assertThat(saved.getSentAt()).isNotNull();
        var action = ((IrisJsonMessageContent) saved.getContent().getLast()).readAs(IrisSolutionChoiceDTO.class);
        assertThat(action).isEqualTo(new IrisSolutionChoiceDTO(10L, IrisSolutionChoice.SHOW_SOLUTION));
    }

    @Test
    void rejectsRepeatedOrStaleChoice() {
        when(messages.findLatestVisibleMessageId(1L)).thenReturn(Optional.of(11L));
        assertThatThrownBy(() -> repository.appendSolutionChoice(1L, new IrisMessage(), new IrisSolutionChoiceRequestDTO(10L, IrisSolutionChoice.SHOW_SOLUTION)))
                .isInstanceOf(BadRequestException.class);
        verify(messages, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOfferFromAnotherExercise() {
        when(messages.findLatestVisibleMessageId(1L)).thenReturn(Optional.of(10L));
        when(sessions.findContextById(1L)).thenReturn(Optional.of(new IrisSessionContextDAO(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, 99L, 7L)));
        assertThatThrownBy(() -> repository.appendSolutionChoice(1L, new IrisMessage(), new IrisSolutionChoiceRequestDTO(10L, IrisSolutionChoice.TRY_MYSELF)))
                .isInstanceOf(BadRequestException.class);
        verify(messages, never()).saveAndFlush(any());
    }
}
