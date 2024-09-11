package de.tum.cit.aet.artemis.communication.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.domain.enumeration.DisplayPriority;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

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
    @Column(name = "display_priority", columnDefinition = "varchar(25) default 'NONE'")
    private DisplayPriority displayPriority = DisplayPriority.NONE;

    @OneToOne
    @JoinColumn(name = "plagiarism_case_id")
    @JsonIncludeProperties({ "id" })
    private PlagiarismCase plagiarismCase;

    @Column(name = "resolved")
    private boolean resolved;

    @Column(name = "answer_count")
    private int answerCount;

    @Column(name = "vote_count")
    private int voteCount;

    public Post() {
    }

    public Post(long id) {
        this.setId(id);
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

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(Boolean resolved) {
        // the case "null" should NOT happen and is only a safety measurement
        this.resolved = resolved != null ? resolved : false;
    }

    public int getAnswerCount() {
        return answerCount;
    }

    public void setAnswerCount(Integer answerCount) {
        // the case "null" should NOT happen and is only a safety measurement
        this.answerCount = answerCount != null ? answerCount : 0;
    }

    public int getVoteCount() {
        return voteCount;
    }

    public void setVoteCount(Integer voteCount) {
        // the case "null" should NOT happen and is only a safety measurement
        this.voteCount = voteCount != null ? voteCount : 0;
    }

    /**
     * Helper method to extract the course a Post belongs to, which is found in different locations based on the Post's context
     *
     * @return the course Post belongs to
     */
    @JsonIgnore
    @Override
    public Course getCoursePostingBelongsTo() {
        if (this.plagiarismCase != null) {
            return this.plagiarismCase.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }
        else if (this.conversation != null) {
            return this.conversation.getCourse();
        }

        return null;
    }

    @Override
    public String toString() {
        return "Post{" + "id=" + getId() + ", content='" + getContent() + "'" + ", creationDate='" + getCreationDate() + "'" + ", visibleForStudents='" + isVisibleForStudents()
                + "'" + ", displayPriority='" + getDisplayPriority() + "'" + "}";
    }
}
