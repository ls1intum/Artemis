package de.tum.cit.aet.artemis.iris.domain.session;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.User;

/**
 * An IrisTutorSuggestionSession represents a conversation between a user and an LLM in the context of a tutor suggestion.
 * This is used for tutors receiving assistance from Iris for answering student questions.
 */
@Entity
@DiscriminatorValue("TUTOR_SUGGESTION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTutorSuggestionSession extends IrisChatSession {

    @JsonIgnore
    private Long postId;

    public IrisTutorSuggestionSession(Long postId, User user) {
        super(user);
        this.postId = postId;
    }

    public IrisTutorSuggestionSession() {
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    @Override
    public String toString() {
        return "IrisTutorSuggestionSession{" + "userId=" + getUserId() + "," + "postId=" + postId + '}';
    }

    @Override
    public boolean shouldSelectLLMUsage() {
        return false;
    }

    @Override
    public IrisChatMode getMode() {
        return IrisChatMode.TUTOR_SUGGESTION;
    }
}
