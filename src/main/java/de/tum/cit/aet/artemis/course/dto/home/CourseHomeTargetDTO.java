package de.tum.cit.aet.artemis.course.dto.home;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeTargetDTO(Kind kind, long id, @Nullable Long conversationId, @Nullable Long focusReplyId) {

    public enum Kind {
        EXERCISE, LECTURE, FAQ, THREAD, IRIS_SESSION, EXAM, STATISTICS, CALENDAR, LEARNING_PATH
    }
}
