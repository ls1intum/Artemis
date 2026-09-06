package de.tum.cit.aet.artemis.communication.domain;

import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * A message in the communication system which can be answered using {@link AnswerPost}.
 */
@Entity
@PostConstraints
@Table(name = "post")
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

    @ManyToOne
    private Conversation conversation;

    // TODO: convert to real database enum
    @Enumerated(EnumType.STRING)
    @Column(name = "display_priority", columnDefinition = "varchar(25) default 'NONE'")
    private DisplayPriority displayPriority = DisplayPriority.NONE;

    // TODO: we should convert this to "Long plagiarismCaseId" to avoid performance issues. The plagiarism case is only needed in very specific cases, so do not load it by default!
    @OneToOne
    @JoinColumn(name = "plagiarism_case_id")
    @JsonIncludeProperties({ "id" })
    private PlagiarismCase plagiarismCase;

    @Column(name = "resolved")
    private boolean resolved;

    @Transient
    private boolean isSaved = false;

    @Column(name = "has_forwarded_messages")
    private boolean hasForwardedMessages;

    /**
     * Monotonic version of this thread's Course Memory state, minted once per ingestion or deletion
     * Artemis dispatches for the thread (see {@code CourseMemoryIngestionService}). Pyris keeps the
     * highest version it has seen per thread and drops older operations, so webhooks that are accepted
     * or finish out of order can neither resurrect a retracted entry nor overwrite a newer edit.
     * <p>
     * Deliberately never written through the entity: {@code insertable} and {@code updatable} are off so
     * a {@code save(post)} on an instance loaded before another node minted a version cannot roll the
     * counter back. The only writer is {@code ConversationMessageRepository#mintCourseMemoryVersion}, which
     * increments atomically in the database; new rows start at the column default of 0.
     */
    @Column(name = "course_memory_version", nullable = false, insertable = false, updatable = false)
    @JsonIgnore
    private long courseMemoryVersion;

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

    public boolean getHasForwardedMessages() {
        return hasForwardedMessages;
    }

    public void setHasForwardedMessages(boolean hasForwardedMessages) {
        this.hasForwardedMessages = hasForwardedMessages;
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

    /**
     * @return the Course Memory version as loaded with this instance; may be stale, see the field. Mint a new one
     *         through the repository rather than incrementing this value.
     */
    @JsonIgnore
    public long getCourseMemoryVersion() {
        return courseMemoryVersion;
    }

    public void setResolved(Boolean resolved) {
        // the case "null" should NOT happen and is only a safety measurement
        this.resolved = resolved != null ? resolved : false;
    }

    @JsonProperty("isSaved")
    public boolean getIsSaved() {
        return isSaved;
    }

    public void setIsSaved(boolean isSaved) {
        this.isSaved = isSaved;
    }

    /**
     * Helper method to extract the course a Post belongs to, which is found in different locations based on the Post's context
     *
     * @return the course Post belongs to
     */
    @JsonIgnore
    @Override
    @Nullable
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
