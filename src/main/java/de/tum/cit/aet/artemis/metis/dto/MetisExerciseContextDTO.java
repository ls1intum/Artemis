package de.tum.cit.aet.artemis.metis.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;

/**
 * What the generation prompt tells the LLM about a programming exercise: its title, its problem statement and its programming language. A projection of
 * {@code MetisExerciseConfigRepository.findExerciseContext}; no endpoint returns it.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MetisExerciseContextDTO(long exerciseId, String title, @Nullable String problemStatement, @Nullable ProgrammingLanguage programmingLanguage) {
}
