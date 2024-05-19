package de.tum.in.www1.artemis.web.rest.dto.examevent;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A DTO for the {@link de.tum.in.www1.artemis.domain.exam.event.ExamLiveEvent} entity.
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
public abstract class ExamLiveEventDTO {

    private Long id;

    private String createdBy;

    private Instant createdDate;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExamLiveEventDTO that = (ExamLiveEventDTO) o;
        return Objects.equals(id, that.id) && Objects.equals(createdBy, that.createdBy) && Objects.equals(createdDate, that.createdDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdBy, createdDate);
    }
}
