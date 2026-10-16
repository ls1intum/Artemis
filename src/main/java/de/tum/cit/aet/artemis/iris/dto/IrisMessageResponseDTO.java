package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageResponseDTO(@Nullable Long id, @Nullable ZonedDateTime sentAt, @Nullable Boolean helpful, IrisMessageSender sender, @Nullable IrisMessageOrigin origin,
        @Nullable IrisProactiveOutcome proactiveOutcome, @Nullable String proactiveEpisodeId, List<IrisMessageContentResponseDTO> content,
        @Nullable List<MemirisMemoryDTO> accessedMemories, @Nullable List<MemirisMemoryDTO> createdMemories, @Nullable List<PyrisActivityDTO> activities,
        @Nullable Integer messageDifferentiator, @JsonProperty("final") @Nullable Boolean finalResult) {

    /**
     * Creates a response DTO from an {@link IrisMessage} entity.
     *
     * @param message the message entity to convert
     * @return the corresponding response DTO
     */
    public static IrisMessageResponseDTO of(IrisMessage message) {
        var content = message.getContent();
        List<IrisMessageContentResponseDTO> contentDTOs = content == null ? List.of() : content.stream().map(IrisMessageContentResponseDTO::of).toList();
        var accessedMemories = message.getAccessedMemories();
        var createdMemories = message.getCreatedMemories();
        var activities = message.getToolActivity();
        var finalResult = Boolean.TRUE.equals(message.getIntermediate()) ? Boolean.FALSE : null;
        return new IrisMessageResponseDTO(message.getId(), message.getSentAt(), message.getHelpful(), message.getSender(), message.getOrigin(), message.getProactiveOutcome(),
                message.getProactiveEpisodeId(), contentDTOs, accessedMemories == null || accessedMemories.isEmpty() ? null : accessedMemories,
                createdMemories == null || createdMemories.isEmpty() ? null : createdMemories, activities == null || activities.isEmpty() ? null : activities,
                message.getMessageDifferentiator(), finalResult);
    }

    /**
     * Creates the response DTOs of a session's message list, leaving out {@link IrisMessageSender#SUMMARY SUMMARY} messages. A summary only replaces older messages in
     * the history Iris reads; clients show every original message instead, and the iOS and VS Code clients would otherwise render it as an empty assistant message.
     *
     * @param messages the session's messages, in display order
     * @return the response DTOs of the messages a client displays, in the same order
     */
    public static List<IrisMessageResponseDTO> ofDisplayed(List<IrisMessage> messages) {
        return messages.stream().filter(message -> message.getSender() != IrisMessageSender.SUMMARY).map(IrisMessageResponseDTO::of).toList();
    }
}
