package de.tum.cit.aet.artemis.iris.dto;

import java.time.ZonedDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisMessageResponseDTO(@Nullable Long id, @Nullable ZonedDateTime sentAt, @Nullable Boolean helpful, IrisMessageSender sender,
        List<IrisMessageContentResponseDTO> content, @Nullable List<MemirisMemoryDTO> accessedMemories, @Nullable List<MemirisMemoryDTO> createdMemories,
        @Nullable Integer messageDifferentiator) {

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
        return new IrisMessageResponseDTO(message.getId(), message.getSentAt(), message.getHelpful(), message.getSender(), contentDTOs,
                accessedMemories == null || accessedMemories.isEmpty() ? null : accessedMemories, createdMemories == null || createdMemories.isEmpty() ? null : createdMemories,
                message.getMessageDifferentiator());
    }
}
