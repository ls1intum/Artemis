package de.tum.cit.aet.artemis.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * DTO for creating a Post with only the necessary fields.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatePostDTO(String content, String title, boolean hasForwardedMessages, Conversation conversation, PlagiarismCase plagiarismCase) {

    public Post toEntity() {
        Post post = new Post();
        post.setContent(this.content);
        post.setTitle(this.title);
        post.setHasForwardedMessages(this.hasForwardedMessages);
        post.setConversation(this.conversation);
        post.setPlagiarismCase(this.plagiarismCase);
        return post;
    }

}
