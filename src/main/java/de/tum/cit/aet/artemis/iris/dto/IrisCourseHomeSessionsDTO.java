package de.tum.cit.aet.artemis.iris.dto;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.dto.home.CourseHomeDTO.SectionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record IrisCourseHomeSessionsDTO(SectionStatus status, List<Item> items) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Item(long id, @Nullable String title, String mode, long entityId, @Nullable String entityName, Instant lastActivity) {
    }
}
