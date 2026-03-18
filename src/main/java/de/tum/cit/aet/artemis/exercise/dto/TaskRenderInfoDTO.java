package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TaskRenderInfoDTO(String taskName, List<Long> testIds) implements Serializable {
}
