package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupDetailGroupData;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailGroupDTO(long id, @NotNull String title, @NotNull String language, boolean isOnline, @NotNull String teachingAssistantName,
        @NotNull String teachingAssistantLogin, String teachingAssistantImageUrl, Integer capacity, String campus, Long groupChannelId, Long tutorChatId,
        @NotNull List<TutorialGroupDetailSessionDTO> sessions) {

    public static TutorialGroupDetailGroupDTO from(TutorialGroupDetailGroupData data, List<TutorialGroupDetailSessionDTO> sessions, Long tutorChatId) {
        return new TutorialGroupDetailGroupDTO(data.groupId(), data.title(), data.language(), data.isOnline(), data.teachingAssistantName(), data.teachingAssistantLogin(),
                data.teachingAssistantImageUrl(), data.capacity(), data.campus(), data.groupChannelId(), tutorChatId, sessions);
    }
}
