package de.tum.cit.aet.artemis.web.rest.dto.examevent;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A DTO for the {@link de.tum.cit.aet.artemis.exam.domain.event.ExamLiveEvent} entity.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
// @formatter:off
@JsonSubTypes({
    @JsonSubTypes.Type(value = ExamWideAnnouncementEventDTO.class, name = "examWideAnnouncement"),
    @JsonSubTypes.Type(value = WorkingTimeUpdateEventDTO.class, name = "workingTimeUpdate"),
    @JsonSubTypes.Type(value = ExamAttendanceCheckEventDTO.class, name = "examAttendanceCheck"),
    @JsonSubTypes.Type(value = ProblemStatementUpdateEventDTO.class, name = "problemStatementUpdate"),
})
// @formatter:on
public interface ExamLiveEventBaseDTO {

    Long id();

    String createdBy();

    Instant createdDate();
}
