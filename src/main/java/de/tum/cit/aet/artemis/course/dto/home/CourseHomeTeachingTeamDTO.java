package de.tum.cit.aet.artemis.course.dto.home;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CourseHomeTeachingTeamDTO(List<Member> items, boolean hasMore, int page) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record Member(long id, String name, @Nullable String imageUrl, String role) {
    }
}
