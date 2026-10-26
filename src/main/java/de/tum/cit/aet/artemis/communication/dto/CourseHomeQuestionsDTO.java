package de.tum.cit.aet.artemis.communication.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.CourseHomeQuestionState;
import de.tum.cit.aet.artemis.course.domain.CourseHomeSectionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeQuestionsDTO(CourseHomeSectionStatus status, List<ItemDTO> items) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ItemDTO(long postId, long conversationId, String title, long totalReplies, CourseHomeQuestionState state, @Nullable Long unreadReplies, Instant latestActivity,
            @Nullable Long focusReplyId) {
    }
}
