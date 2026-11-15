package de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a webhook data transfer object for an FAQ in the Pyris system.
 * This DTO is used to encapsulate the information related to the faqs
 * providing necessary details such as faqId the content as questionTitle and questionAnswer as well as the course description.
 * The content fingerprint is stored with the FAQ in Iris and reported back by the FAQ census, so Artemis can tell which FAQs are out of date.
 * Empty values are omitted, so a deletion of an FAQ that no longer exists sends only the ids.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisFaqWebhookDTO(long faqId, @Nullable String questionTitle, @Nullable String questionAnswer, long courseId, @Nullable String courseName,
        @Nullable String courseDescription, @Nullable String contentFingerprint) {
}
