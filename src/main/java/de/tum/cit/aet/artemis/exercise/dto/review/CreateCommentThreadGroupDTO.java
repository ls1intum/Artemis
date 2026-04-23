package de.tum.cit.aet.artemis.exercise.dto.review;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Request payload to create a new comment thread group.")
public record CreateCommentThreadGroupDTO(@Schema(description = "Thread ids to associate with the new group.") @NotNull @Size(min = 2) List<@NotNull Long> threadIds) {
}
