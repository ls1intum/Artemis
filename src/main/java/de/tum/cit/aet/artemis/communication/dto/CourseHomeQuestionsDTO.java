package de.tum.cit.aet.artemis.communication.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO.SectionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeQuestionsDTO(SectionStatus status, List<Item> items) {

    public enum State {
        AWAITING_REPLY, ANSWERED, RESOLVED
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Item(long postId, long conversationId, String title, long totalReplies, State state, @Nullable Long unreadReplies, Instant latestActivity,
            @Nullable Long focusReplyId) {
    }
}
