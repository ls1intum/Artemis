package de.tum.cit.aet.artemis.course.dto.home;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.CourseHomeTargetKind;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeTargetDTO(CourseHomeTargetKind kind, long id, @Nullable Long conversationId, @Nullable Long focusReplyId) {

}
