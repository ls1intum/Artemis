package de.tum.cit.aet.artemis.admin.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Aggregated, anonymous communication numbers of one semester. Contains no content and no authors.
 *
 * @param posts     number of posts in channels, group chats and direct messages of the counted courses
 * @param replies   number of replies to these posts
 * @param reactions number of reactions to these posts and replies
 * @param faqs      number of published FAQs of the counted courses
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record CommunicationUsageDTO(long posts, long replies, long reactions, long faqs) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
