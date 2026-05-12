package de.tum.cit.aet.artemis.programming.dto;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record AutomaticAfterDueDatePreviewRequestDTO(@Nullable Long programmingExerciseId, @Nullable Long examId, @Nullable ZonedDateTime dueDate,
        @Nullable Boolean hasAfterDueDateBuildPhase, @Nullable ProgrammingLanguage programmingLanguage, @Nullable ProjectType projectType,
        @Nullable Boolean staticCodeAnalysisEnabled, @Nullable Boolean sequentialTestRuns) {
}
