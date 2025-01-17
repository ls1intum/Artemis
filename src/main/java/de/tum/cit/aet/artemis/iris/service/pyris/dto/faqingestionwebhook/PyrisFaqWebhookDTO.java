package de.tum.cit.aet.artemis.iris.service.pyris.dto.faqingestionwebhook;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a webhook data transfer object for an FAQ in the Pyris system.
 * This DTO is used to encapsulate the information related to the faqs
 * providing necessary details such as faqId the content as questionTitle and questionAnswer as well as the course description.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)

public record PyrisFaqWebhookDTO(long faqId, String questionTitle, String questionAnswer, long courseId, String courseName, String courseDescription) {
}
