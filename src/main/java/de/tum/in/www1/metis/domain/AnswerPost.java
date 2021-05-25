package de.tum.in.www1.metis.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.Course;

/**
 * An answer post reaction on a root post.
 */
@Entity
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Table(name = "answer_post")
public class AnswerPost extends Post {

    @Column(name = "tutor_approved")
    private Boolean tutorApproved;

    @ManyToOne
    @JsonIgnoreProperties("answers")
    private RootPost rootPost;

    public Boolean getTutorApproved() {
        return tutorApproved;
    }

    public void setTutorApproved(Boolean tutorApproved) {
        this.tutorApproved = tutorApproved;
    }

    public RootPost getRootPost() {
        return rootPost;
    }

    public void setRootPost(RootPost rootPost) {
        this.rootPost = rootPost;
    }

    /**
     * Convenience method to retrieve the relevant course from linked context.
     *
     * @return related Course object
     */
    @Override
    public Course getCourse() {
        return rootPost.getCourse();
    }
}
