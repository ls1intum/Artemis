package de.tum.cit.aet.artemis.programming.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The part of an exported {@code Exercise-Details-*.json} the import from file reads: the title of the exercise the
 * archive was created from, which the import writes into the placeholders of the imported repositories.
 * <p>
 * {@code ignoreUnknown} is load-bearing: the file carries the whole exercise, and archives of older Artemis versions
 * carry fields the current model no longer has.
 *
 * @param title the title of the exported exercise
 */
@JsonIgnoreProperties(ignoreUnknown = true)
// bare @JsonInclude(): this record is only read, and the shared architecture rule forbids spelling out Include.ALWAYS
@JsonInclude()
public record ExportedExerciseDetailsDTO(String title) {
}
