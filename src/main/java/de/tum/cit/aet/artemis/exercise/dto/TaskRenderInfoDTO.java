package de.tum.cit.aet.artemis.exercise.dto;

import java.io.Serializable;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TaskRenderInfoDTO(String taskName, List<Long> testIds, @Nullable String testStatus, @Nullable Integer successfulTests, @Nullable Integer failedTests)
        implements Serializable {
}
