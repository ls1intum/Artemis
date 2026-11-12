package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * IDs of the source FAQs to copy. Using a set ensures each selected FAQ is imported only once.
 *
 * @param faqIds between one and 500 distinct, positive source FAQ IDs; repeated IDs collapse into one, and source access is rechecked before copying
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImportFaqsDTO(
        @ArraySchema(uniqueItems = true, schema = @Schema(type = "integer", format = "int64", minimum = "1", description = "A source FAQ ID; repeated IDs are imported once")) @NotNull @Size(min = 1, max = 500) Set<@NotNull @Positive Long> faqIds) {
}
