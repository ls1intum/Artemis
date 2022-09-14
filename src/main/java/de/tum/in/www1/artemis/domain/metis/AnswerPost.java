package de.tum.in.www1.artemis.domain.metis;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.in.www1.artemis.domain.Course;

/**
 * An AnswerPost.
 */
@Entity
@Table(name = "answer_post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerPost extends Posting {

    @Column(name = "resolves_post", columnDefinition = "boolean default false")
    private Boolean resolvesPost = false;

    @OneToMany(mappedBy = "answerPost", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Reaction> reactions = new HashSet<>();

    @ManyToOne
    @JsonIncludeProperties({ "id", "exercise", "lecture", "course", "courseWideContext", "author" })
    private Post post;

    @JsonProperty("resolvesPost")
    public Boolean doesResolvePost() {
        return resolvesPost;
    }

    public void setResolvesPost(Boolean resolvesPost) {
        this.resolvesPost = resolvesPost;
    }

    @Override
    public Set<Reaction> getReactions() {
        return reactions;
    }

    @Override
    public void setReactions(Set<Reaction> reactions) {
        this.reactions = reactions;
    }

    @Override
    public void addReaction(Reaction reaction) {
        this.reactions.add(reaction);
    }

    @Override
    public void removeReaction(Reaction reaction) {
        this.reactions.remove(reaction);
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    /**
     * Helper method to extract the course an AnswerPost belongs to, which is found in different locations based on the parent Post's context
     * @return the course AnswerPost belongs to
     */
    @Override
    public Course getCoursePostingBelongsTo() {
        return this.post.getCoursePostingBelongsTo();
    }

    @Override
    public String toString() {
        return "AnswerPost{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", resolvesPosts='" + doesResolvePost() + "'"
                + "}";
    }
}
