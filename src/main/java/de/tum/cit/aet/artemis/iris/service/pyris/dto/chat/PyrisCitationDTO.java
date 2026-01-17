package de.tum.cit.aet.artemis.iris.service.pyris.dto.chat;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisCitationDTO(int index, String type, @Nullable String link, @Nullable String lectureName, @Nullable String unitName, @Nullable String faqQuestionTitle,
        @Nullable String summary, @Nullable String keyword, @Nullable Integer page, @Nullable String startTime, @Nullable String endTime, @Nullable Integer startTimeSeconds,
        @Nullable Integer endTimeSeconds) {
}
