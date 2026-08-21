package de.tum.cit.aet.artemis.iris.domain.message;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * A server-authored ambient struggle decision, recorded when Artemis emits the ambient pointer and consumed
 * when the student reveals it.
 *
 * <p>
 * The ambient flow is a pull model: Artemis tells the client "there is a hint for this episode" without
 * persisting a chat message, and the message is only written once the student clicks reveal. That left the
 * reveal endpoint taking the hint text from the caller, so a student could post arbitrary text as an
 * {@code LLM} / {@code PROACTIVE_STRUGGLE} message, forging assistant history that is fed back into the Pyris
 * prompt, and could mint unlimited rows by sending fresh ids.
 *
 * <p>
 * This record is the server's own copy of what it offered. Reveal looks the decision up by
 * {@code (userId, exerciseId, episodeId)}, persists {@link #hintText} rather than anything the caller sent,
 * and claims the row in the same transaction, so a decision can be revealed exactly once.
 */
@Entity
@Table(name = "iris_ambient_decision")
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisAmbientDecision extends DomainObject {

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "exercise_id", nullable = false)
    private long exerciseId;

    /**
     * The client-allocated episode id this decision belongs to. Never null: a decision without an episode
     * cannot be addressed by a reveal, so those are not recorded at all.
     */
    @Column(name = "episode_id", nullable = false)
    private String episodeId;

    /**
     * The hint text as authored by Pyris. This is what a reveal persists, never the caller's copy.
     */
    @Column(name = "hint_text", nullable = false)
    private String hintText;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    /**
     * Set when a reveal claims this decision. A non-null value means the offer has been used up; the guarded
     * claim query only matches rows where this is still null.
     */
    @Column(name = "consumed_at")
    private ZonedDateTime consumedAt;

    /**
     * The message the reveal created, so a replay can return the same row instead of inserting a second one.
     */
    @Column(name = "consumed_message_id")
    private Long consumedMessageId;

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public long getExerciseId() {
        return exerciseId;
    }

    public void setExerciseId(long exerciseId) {
        this.exerciseId = exerciseId;
    }

    public String getEpisodeId() {
        return episodeId;
    }

    public void setEpisodeId(String episodeId) {
        this.episodeId = episodeId;
    }

    public String getHintText() {
        return hintText;
    }

    public void setHintText(String hintText) {
        this.hintText = hintText;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public ZonedDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(ZonedDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public Long getConsumedMessageId() {
        return consumedMessageId;
    }

    public void setConsumedMessageId(Long consumedMessageId) {
        this.consumedMessageId = consumedMessageId;
    }

    @Override
    public String toString() {
        return "IrisAmbientDecision{" + "id=" + getId() + ", userId=" + userId + ", exerciseId=" + exerciseId + ", episodeId='" + episodeId + '\'' + ", consumedAt=" + consumedAt
                + ", consumedMessageId=" + consumedMessageId + '}';
    }
}
