package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDetailGroupDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailGroupDTO(long id, @NotNull String title, @NotNull String language, boolean isOnline, @NotNull String teachingAssistantName,
        @NotNull String teachingAssistantLogin, @Nullable String teachingAssistantImageUrl, @Nullable Integer capacity, @Nullable String campus, @Nullable Long groupChannelId,
        @Nullable Long tutorChatId, @NotNull List<TutorialGroupDetailSessionDTO> sessions) {

    public static TutorialGroupDetailGroupDTO from(RawTutorialGroupDetailGroupDTO rawDto, List<TutorialGroupDetailSessionDTO> sessions, Long tutorChatId) {
        return new TutorialGroupDetailGroupDTO(rawDto.groupId(), rawDto.title(), rawDto.language(), rawDto.isOnline(), rawDto.teachingAssistantName(),
                rawDto.teachingAssistantLogin(), rawDto.teachingAssistantImageUrl(), rawDto.capacity(), rawDto.campus(), rawDto.groupChannelId(), tutorChatId, sessions);
    }
}
