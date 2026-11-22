package de.tum.cit.aet.artemis.modeling.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A model that is converted to a pdf by the Apollon conversion service.
 *
 * @param model the model as JSON, at most {@value #MAX_MODEL_LENGTH} characters
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApollonModelDTO(@NotBlank @Size(max = ApollonModelDTO.MAX_MODEL_LENGTH, message = "The model is too large.") String model) implements Serializable {

    /** The longest model that is converted, ten times the longest model that students can submit. */
    public static final int MAX_MODEL_LENGTH = 1_000_000;
}
