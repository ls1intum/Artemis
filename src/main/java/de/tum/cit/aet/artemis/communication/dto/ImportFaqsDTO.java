package de.tum.cit.aet.artemis.communication.dto;

import java.util.Set;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * IDs of the source FAQs to copy. Using a set ensures each selected FAQ is imported only once.
 *
 * @param faqIds between one and 500 distinct, positive source FAQ IDs; source access is rechecked before copying
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ImportFaqsDTO(@NotEmpty @Size(max = 500) Set<@NotNull @Positive Long> faqIds) {
}
