package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseSettingsRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

class IrisSettingsExportTest {

    @Test
    void exportPreservesRepositoryOrderAndMessageMetadataAndJoinsOnlyNonemptyContent() {
        var repository = mock(IrisChatSessionRepository.class);
        var api = new IrisSettingsApi(mock(IrisSettingsService.class), mock(IrisCourseSettingsRepository.class), repository, mock(IrisProactiveEpisodeRepository.class));
        var first = new IrisChatSession();
        first.setId(1L);
        first.setUserId(2L);
        var created = ZonedDateTime.parse("2026-01-01T12:00:00Z");
        first.setCreationDate(created);
        var message = new IrisMessage();
        message.setId(3L);
        message.setSender(IrisMessageSender.LLM);
        message.setSentAt(created.plusMinutes(1));
        message.setHelpful(false);
        message.setContent(List.of(new IrisTextMessageContent("first"), new IrisTextMessageContent(null), new IrisTextMessageContent(""), new IrisTextMessageContent("second")));
        var empty = new IrisMessage();
        empty.setContent(List.of());
        var missing = new IrisMessage();
        missing.setContent(null);
        first.setMessages(List.of(message, empty, missing));
        var second = new IrisChatSession();
        second.setId(4L);
        second.setMessages(List.of());
        when(repository.findAllWithMessagesByCourseIdSortedByCreationDate(10L)).thenReturn(List.of(first, second));
        var exported = api.findCourseChatSessionsForExport(10L);
        assertThat(exported).extracting(dto -> dto.sessionId()).containsExactly(1L, 4L);
        assertThat(exported.getFirst().userId()).isEqualTo(2L);
        assertThat(exported.getFirst().creationDate()).isEqualTo(created);
        assertThat(exported.getFirst().messages()).hasSize(3);
        var text = exported.getFirst().messages().getFirst();
        assertThat(text.id()).isEqualTo(3L);
        assertThat(text.sentAt()).isEqualTo(created.plusMinutes(1));
        assertThat(text.sender()).isEqualTo("LLM");
        assertThat(text.helpful()).isFalse();
        assertThat(text.content()).isEqualTo("first\nsecond");
        assertThat(exported.getFirst().messages().get(1).content()).isNull();
        assertThat(exported.getFirst().messages().get(1).sender()).isNull();
        assertThat(exported.getFirst().messages().get(2).content()).isNull();
        assertThat(exported.get(1).messages()).isEmpty();
    }
}
