package de.tum.cit.aet.artemis.iris.domain.session;

import java.util.Optional;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * An IrisTutorSuggestionSession represents a conversation between a user and an LLM in the context of a tutor suggestion.
 * This is used for tutors receiving assistance from Iris for answering student questions.
 */
@Entity
@DiscriminatorValue("TUTOR_SUGGESTION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisTutorSuggestionSession extends IrisChatSession {

    @ManyToOne
    @JsonIgnore
    private Post post;

    public IrisTutorSuggestionSession(Post post, User user) {
        super(user);
        this.post = post;
    }

    public IrisTutorSuggestionSession() {
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    @Override
    public String toString() {
        return "IrisTutorSuggestionSession{" + "user=" + Optional.ofNullable(getUser()).map(User::getLogin).orElse("null") + "," + "post=" + post + '}';
    }
}
