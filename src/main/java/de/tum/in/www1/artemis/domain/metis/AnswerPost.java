package de.tum.in.www1.artemis.domain.metis;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 * An AnswerPost.
 */
@Entity
@Table(name = "answer_post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerPost extends Posting {

    @Column(name = "tutor_approved")
    private Boolean tutorApproved;

    // To be used with introduction of Metis
    @OneToMany(mappedBy = "answerPost", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Reaction> reactions = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("answers")
    private Post post;

    public Boolean isTutorApproved() {
        return tutorApproved;
    }

    public void setTutorApproved(Boolean tutorApproved) {
        this.tutorApproved = tutorApproved;
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

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    @Override
    public Course getCourse() {
        if (post == null) {
            return null;
        }
        return post.getCourse();
    }

    @Override
    public String toString() {
        return "AnswerPost{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", tutorApproved='" + isTutorApproved() + "'"
                + "}";
    }
}
