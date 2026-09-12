package de.tum.cit.aet.artemis.iris.domain.message;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.dto.MemirisMemoryDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisActivityDTO;

/**
 * An IrisMessage represents a single message in an IrisSession.
 * The message may contain multiple pieces of content with different types.
 */
@Entity
@Table(name = "iris_message")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisMessage extends DomainObject {

    @ManyToOne(optional = false)
    @JoinColumn(name = "session_id")
    @JsonIgnore
    private IrisSession session;

    @Nullable
    @Column(name = "sent_at")
    private ZonedDateTime sentAt = ZonedDateTime.now();

    @Nullable
    @Column(name = "helpful")
    private Boolean helpful;

    @Column(name = "sender")
    @Enumerated(EnumType.STRING)
    private IrisMessageSender sender;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "origin")
    private IrisMessageOrigin origin;

    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "proactive_outcome")
    private IrisProactiveOutcome proactiveOutcome;

    @Nullable
    @Column(name = "proactive_episode_id")
    private String proactiveEpisodeId;

    /**
     * The exercise the proactive message was decided for, stamped at insert time. Deliberately NOT derived from
     * {@code session.entityId}: a session is born a COURSE_CHAT and its mode/entityId change on every context switch,
     * so the session is not a durable record of which exercise a row belongs to. Episode lookups scope by this column
     * so an episode id reused across two exercises cannot make one exercise's outcome terminal for the other.
     */
    @Nullable
    @Column(name = "proactive_exercise_id")
    private Long proactiveExerciseId;

    @OrderColumn(name = "iris_message_content_order")
    @OneToMany(mappedBy = "message", fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IrisMessageContent> content = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "accessed_memories", columnDefinition = "json")
    private List<MemirisMemoryDTO> accessedMemories = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "created_memories", columnDefinition = "json")
    private List<MemirisMemoryDTO> createdMemories = new ArrayList<>();

    @Nullable
    @Lob
    @Convert(converter = IrisMessageToolActivityConverter.class)
    @Column(name = "tool_activity")
    private List<PyrisActivityDTO> toolActivity;

    @Nullable
    @Column(name = "intermediate")
    private Boolean intermediate;

    @Transient
    private Integer messageDifferentiator; // is supposed to be only a part of the dto and helps the client application to differentiate messages it should add to the message store

    public IrisSession getSession() {
        return session;
    }

    public void setSession(IrisSession session) {
        this.session = session;
    }

    @Nullable
    public ZonedDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(@Nullable ZonedDateTime sentAt) {
        this.sentAt = sentAt;
    }

    @Nullable
    public Boolean getHelpful() {
        return helpful;
    }

    public void setHelpful(@Nullable Boolean helpful) {
        this.helpful = helpful;
    }

    public IrisMessageSender getSender() {
        return sender;
    }

    public void setSender(IrisMessageSender sender) {
        this.sender = sender;
    }

    @Nullable
    public IrisMessageOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(@Nullable IrisMessageOrigin origin) {
        this.origin = origin;
    }

    @Nullable
    public IrisProactiveOutcome getProactiveOutcome() {
        return proactiveOutcome;
    }

    public void setProactiveOutcome(@Nullable IrisProactiveOutcome proactiveOutcome) {
        this.proactiveOutcome = proactiveOutcome;
    }

    @Nullable
    public String getProactiveEpisodeId() {
        return proactiveEpisodeId;
    }

    public void setProactiveEpisodeId(@Nullable String proactiveEpisodeId) {
        this.proactiveEpisodeId = proactiveEpisodeId;
    }

    @Nullable
    public Long getProactiveExerciseId() {
        return proactiveExerciseId;
    }

    public void setProactiveExerciseId(@Nullable Long proactiveExerciseId) {
        this.proactiveExerciseId = proactiveExerciseId;
    }

    public List<IrisMessageContent> getContent() {
        return content;
    }

    public void setContent(List<IrisMessageContent> content) {
        this.content = content;
    }

    public void addContent(IrisMessageContent... content) {
        for (IrisMessageContent messageContent : content) {
            messageContent.setMessage(this);
            this.content.add(messageContent);
        }
    }

    public List<MemirisMemoryDTO> getAccessedMemories() {
        return accessedMemories;
    }

    public void setAccessedMemories(List<MemirisMemoryDTO> accessedMemories) {
        this.accessedMemories = accessedMemories;
    }

    public List<MemirisMemoryDTO> getCreatedMemories() {
        return createdMemories;
    }

    public void setCreatedMemories(List<MemirisMemoryDTO> createdMemories) {
        this.createdMemories = createdMemories;
    }

    @Nullable
    public List<PyrisActivityDTO> getToolActivity() {
        return toolActivity;
    }

    public void setToolActivity(@Nullable List<PyrisActivityDTO> toolActivity) {
        this.toolActivity = toolActivity;
    }

    @Nullable
    public Boolean getIntermediate() {
        return intermediate;
    }

    public void setIntermediate(@Nullable Boolean intermediate) {
        this.intermediate = intermediate;
    }

    @JsonProperty
    public Integer getMessageDifferentiator() {
        return messageDifferentiator;
    }

    @JsonProperty
    public void setMessageDifferentiator(Integer messageDifferentiator) {
        this.messageDifferentiator = messageDifferentiator;
    }

    @Override
    public String toString() {
        return "IrisMessage{" + "id=" + getId() + ", session=" + session + ", sentAt=" + sentAt + ", helpful=" + helpful + ", sender=" + sender + ", content=" + content
                + ", toolActivity=" + toolActivity + ", intermediate=" + intermediate + ", messageDifferentiator=" + messageDifferentiator + '}';
    }
}
