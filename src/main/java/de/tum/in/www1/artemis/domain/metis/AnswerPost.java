package de.tum.in.www1.artemis.domain.metis;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 * A AnswerPost.
 */
@Entity
@Table(name = "answer_post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AnswerPost extends Posting {

    @Column(name = "tutor_approved")
    private Boolean tutorApproved;

    @ManyToOne
    @JsonIgnoreProperties("answers")
    private Post post;

    public Boolean isTutorApproved() {
        return tutorApproved;
    }

    public void setTutorApproved(Boolean tutorApproved) {
        this.tutorApproved = tutorApproved;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    @Override
    public String toString() {
        return "AnswerPost{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", tutorApproved='" + isTutorApproved() + "'"
                + "}";
    }

    @Override
    public Course getCourse() {
        if (post == null) {
            return null;
        }
        return post.getCourse();
    }
}
