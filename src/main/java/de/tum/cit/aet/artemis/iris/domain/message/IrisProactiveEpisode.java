package de.tum.cit.aet.artemis.iris.domain.message;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import de.tum.cit.aet.artemis.core.domain.DomainObject;

/**
 * One proactive struggle episode, registered when Artemis accepts a trigger for it and holding that episode's
 * terminal outcome.
 *
 * <p>
 * The row exists so the episode has an identity that can be locked. Before it, every terminal decision was a
 * check-then-act: a callback read {@code isEpisodeTerminal} and wrote its message in a separate statement, so a
 * dismiss committing in between produced a hint the student had already closed. There was nothing to serialize on.
 * The chat session cannot serve: sessions are resolved as "the latest for (user, exercise)" with no uniqueness
 * constraint, so an episode's existing message and its next append can sit in different sessions and take different
 * locks. The episode could not lock itself either, because before its first message row it had no row.
 *
 * <p>
 * Registering at trigger time is what makes the second half work. The episode id is client-allocated and arrives
 * synchronously in the trigger request, before any Pyris callback can run, so the row is always there when a
 * callback or an outcome write needs it. That also makes a dismiss arriving before the first message representable:
 * it is written here, rather than deferred until some row exists for it to sit on.
 *
 * <p>
 * {@link #outcome} is the authoritative terminal state. {@code iris_message.proactive_outcome} keeps being written
 * as a subordinate mirror, because the history replayed to Pyris and the message DTO both read it from the row.
 *
 * <p>
 * The row also carries the episode's ambient offer ({@link #hintText}, {@link #consumedAt},
 * {@link #consumedMessageId}). It has the same {@code (user, exercise, episode)} grain, so a table of its own would
 * buy a second unique key, a second lock and an insert race: the episode is registered at trigger time, which makes
 * the offer an update of a row the caller already holds the lock on.
 * The three columns are nullable because a registered episode legitimately has no offer yet; that a consumed
 * offer carries both its text and its message is enforced by the single writer, not by a constraint.
 */
@Entity
@Table(name = "iris_proactive_episode")
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisProactiveEpisode extends DomainObject {

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "exercise_id", nullable = false)
    private long exerciseId;

    /**
     * The client-allocated episode id. Never null: an episode without an id cannot be addressed by any callback or
     * endpoint, so those are never registered and keep the pre-registry behaviour.
     */
    @Column(name = "episode_id", nullable = false)
    private String episodeId;

    /**
     * How the episode ended, or null while it is still open. First-terminal-wins: once set, it is never overwritten,
     * which the guarded update enforces in one statement.
     */
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome")
    private IrisProactiveOutcome outcome;

    /**
     * When this episode was last triggered. Registration sets it and every repeat trigger refreshes it, so
     * retention can ask "no trigger for this episode in seven days" rather than "registered seven days ago". An
     * episode that is still being triggered is therefore never reaped out from under a run in flight.
     */
    @Column(name = "last_triggered_at", nullable = false)
    private ZonedDateTime lastTriggeredAt;

    /**
     * The ambient hint as authored by Pyris, or null while this episode has been offered nothing. A reveal
     * persists this text, never the caller's copy, which is what stops a student from authoring assistant history.
     */
    @Nullable
    @Column(name = "hint_text")
    private String hintText;

    /**
     * Set when a reveal claims the offer. Non-null means the hint has been used up, so a second reveal returns the
     * first one's message instead of writing another.
     */
    @Nullable
    @Column(name = "consumed_at")
    private ZonedDateTime consumedAt;

    /**
     * The message the reveal created, so a replay returns the same row rather than inserting a second one.
     */
    @Nullable
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

    @Nullable
    public IrisProactiveOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(@Nullable IrisProactiveOutcome outcome) {
        this.outcome = outcome;
    }

    public ZonedDateTime getLastTriggeredAt() {
        return lastTriggeredAt;
    }

    public void setLastTriggeredAt(ZonedDateTime lastTriggeredAt) {
        this.lastTriggeredAt = lastTriggeredAt;
    }

    @Nullable
    public String getHintText() {
        return hintText;
    }

    public void setHintText(@Nullable String hintText) {
        this.hintText = hintText;
    }

    @Nullable
    public ZonedDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(@Nullable ZonedDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    @Nullable
    public Long getConsumedMessageId() {
        return consumedMessageId;
    }

    public void setConsumedMessageId(@Nullable Long consumedMessageId) {
        this.consumedMessageId = consumedMessageId;
    }

    @Override
    public String toString() {
        return "IrisProactiveEpisode{" + "id=" + getId() + ", userId=" + userId + ", exerciseId=" + exerciseId + ", episodeId='" + episodeId + '\'' + ", outcome=" + outcome
                + ", consumedAt=" + consumedAt + '}';
    }
}
