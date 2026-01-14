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
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.communication.domain.conversation.Conversation;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;

/**
 * A message in the communication system which can be answered using {@link AnswerPost}.
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
