package de.tum.cit.aet.artemis.communication.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * The fields of an accepted FAQ that decide what Iris must hold for it, read without loading the entity.
 *
 * @param faqId          the FAQ
 * @param courseId       its course
 * @param questionTitle  the question
 * @param questionAnswer the answer
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record FaqFingerprintInputDTO(long faqId, long courseId, @Nullable String questionTitle, @Nullable String questionAnswer) {
}
