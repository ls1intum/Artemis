package de.tum.in.www1.artemis.domain.metis;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.*;

/**
 * A Post, i.e. start of a Metis thread.
 */
@Entity
@EitherOr
@Table(name = "post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Post extends Posting {

    // To be used with introduction of Metis
    @Size(max = 200)
    @Column(name = "title")
    private String title;

    @Column(name = "visible_for_students")
    private Boolean visibleForStudents;

    /**
     * Track the votes for a "Post"
     *
     * @deprecated This will be removed with the introduction of Metis, where every Post will have a emoji reaction bar.
     */
    @Deprecated
    @Column(name = "votes", columnDefinition = "integer default 0")
    private Integer votes = 0;

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<AnswerPost> answers = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tags")
    private Set<String> tags = new HashSet<>();

    @ManyToOne
    @JsonIgnoreProperties("posts")
    private Exercise exercise;

    @ManyToOne
    @JsonIgnoreProperties("posts")
    private Lecture lecture;

    @ManyToOne
    @JsonIgnoreProperties("posts")
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_wide_context")
    private CourseWideContext courseWideContext;

    public void setCourse(Course course) {
        this.course = course;
    }

    public CourseWideContext getCourseWideContext() {
        return courseWideContext;
    }

    public void setCourseWideContext(CourseWideContext courseWideContext) {
        this.courseWideContext = courseWideContext;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Boolean isVisibleForStudents() {
        return visibleForStudents;
    }

    public void setVisibleForStudents(Boolean visibleForStudents) {
        this.visibleForStudents = visibleForStudents;
    }

    public Integer getVotes() {
        return votes;
    }

    public void setVotes(Integer votes) {
        this.votes = votes;
    }

    public Set<AnswerPost> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<AnswerPost> answerPosts) {
        this.answers = answerPosts;
    }

    public Exercise getExercise() {
        return exercise;
    }

    public void setExercise(Exercise exercise) {
        this.exercise = exercise;
    }

    public Lecture getLecture() {
        return lecture;
    }

    public void setLecture(Lecture lecture) {
        this.lecture = lecture;
    }

    /**
     * Convenience method to retrieve the relevant Course, if necessary from linked Lecture or Exercise.
     *
     * @return related Course object
     */
    @Override
    public Course getCourse() {
        if (getLecture() != null) {
            return getLecture().getCourse();
        }
        else if (getExercise() != null) {
            return getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        else if (getCourse() != null) {
            return getCourse();
        }
        return null;
    }

    @Override
    public String toString() {
        return "Post{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", visibleForStudents='" + isVisibleForStudents()
                + "'" + ", votes='" + getVotes() + "'" + "}";
    }
}
