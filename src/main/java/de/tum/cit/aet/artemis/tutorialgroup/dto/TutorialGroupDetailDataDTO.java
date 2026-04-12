package de.tum.cit.aet.artemis.tutorialgroup.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.tutorialgroup.util.RawTutorialGroupDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TutorialGroupDetailDataDTO(@NotNull Long id, @NotNull String title, @NotNull String language, @NotNull Boolean isOnline, @NotNull String tutorName,
        @NotNull String tutorLogin, @NotNull Long tutorId, @Nullable String tutorImageUrl, @Nullable Integer capacity, @Nullable String campus,
        @Nullable String additionalInformation, @Nullable Long groupChannelId, @Nullable Long tutorChatId, @NotNull List<TutorialGroupSessionDTO> sessions) {

    public static TutorialGroupDetailDataDTO from(RawTutorialGroupDTO rawDto, List<TutorialGroupSessionDTO> sessions, Long tutorChatId) {
        return new TutorialGroupDetailDataDTO(rawDto.groupId(), rawDto.title(), rawDto.language(), rawDto.isOnline(), rawDto.tutorName(), rawDto.tutorLogin(), rawDto.tutorId(),
                rawDto.tutorImageUrl(), rawDto.capacity(), rawDto.campus(), rawDto.additionalInformation(), rawDto.groupChannelId(), tutorChatId, sessions);
    }
}
