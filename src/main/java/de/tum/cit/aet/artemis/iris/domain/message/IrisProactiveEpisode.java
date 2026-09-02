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

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

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

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "IrisProactiveEpisode{" + "id=" + getId() + ", userId=" + userId + ", exerciseId=" + exerciseId + ", episodeId='" + episodeId + '\'' + ", outcome=" + outcome + '}';
    }
}
