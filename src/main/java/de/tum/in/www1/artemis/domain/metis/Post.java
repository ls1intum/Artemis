package de.tum.in.www1.artemis.domain.metis;

import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;
import javax.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.enumeration.DisplayPriority;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;

/**
 * A Post, i.e. start of a Metis thread.
 */
@Entity
@PostConstraints
@Table(name = "post")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class Post extends Posting {

    @Size(max = 200)
    @Column(name = "title")
    private String title;

    @Column(name = "visible_for_students")
    private Boolean visibleForStudents;

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<Reaction> reactions = new HashSet<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<AnswerPost> answers = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "post_tag", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "text")
    private Set<String> tags = new HashSet<>();

    @ManyToOne
    @JsonIncludeProperties({ "id", "title" })
    private Exercise exercise;

    @ManyToOne
    @JsonIncludeProperties({ "id", "title" })
    private Lecture lecture;

    @ManyToOne
    @JsonIncludeProperties({ "id", "title" })
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "course_wide_context")
    private CourseWideContext courseWideContext;

    @ManyToOne
    private Conversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_priority")
    private DisplayPriority displayPriority;

    @OneToOne
    @JoinColumn(name = "plagiarism_case_id")
    @JsonIncludeProperties({ "id" })
    private PlagiarismCase plagiarismCase;

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

    public Set<AnswerPost> getAnswers() {
        return answers;
    }

    public void setAnswers(Set<AnswerPost> answerPosts) {
        this.answers = answerPosts;
    }

    public void addAnswerPost(AnswerPost answerPost) {
        this.answers.add(answerPost);
    }

    public void removeAnswerPost(AnswerPost answerPost) {
        this.answers.remove(answerPost);
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    public void addTag(String tag) {
        this.tags.add(tag);
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

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public CourseWideContext getCourseWideContext() {
        return courseWideContext;
    }

    public void setCourseWideContext(CourseWideContext courseWideContext) {
        this.courseWideContext = courseWideContext;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }

    public DisplayPriority getDisplayPriority() {
        return displayPriority;
    }

    public void setDisplayPriority(DisplayPriority displayPriority) {
        this.displayPriority = displayPriority;
    }

    public PlagiarismCase getPlagiarismCase() {
        return plagiarismCase;
    }

    public void setPlagiarismCase(PlagiarismCase plagiarismCase) {
        this.plagiarismCase = plagiarismCase;
    }

    /**
     * Helper method to determine if a given post has the same context, i.e. either same exercise, lecture or course-wide context
     * @param otherPost post that is compared to
     * @return boolean flag indicating if same context or not
     */
    public boolean hasSameContext(Post otherPost) {
        if (getExercise() != null && otherPost.getExercise() != null && getExercise().getId().equals(otherPost.getExercise().getId())) {
            return true;
        }
        else if (getLecture() != null && otherPost.getLecture() != null && getLecture().getId().equals(otherPost.getLecture().getId())) {
            return true;
        }
        else if (getPlagiarismCase() != null && otherPost.getPlagiarismCase() != null && getPlagiarismCase().getId().equals(otherPost.getPlagiarismCase().getId())) {
            return true;
        }
        return getCourseWideContext() != null && otherPost.getCourseWideContext() != null && getCourseWideContext() == otherPost.getCourseWideContext();
    }

    /**
     * Helper method to extract the course a Post belongs to, which is found in different locations based on the Post's context
     * @return the course Post belongs to
     */
    @Override
    public Course getCoursePostingBelongsTo() {
        if (this.course != null) {
            return this.course;
        }
        else if (this.lecture != null) {
            return this.lecture.getCourse();
        }
        else if (this.exercise != null) {
            return this.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        else if (this.plagiarismCase != null) {
            return this.plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return null;
    }

    @Override
    public String toString() {
        return "Post{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", visibleForStudents='" + isVisibleForStudents()
                + "'" + ", displayPriority='" + getDisplayPriority() + "'" + "}";
    }
}
