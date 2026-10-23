package de.tum.cit.aet.artemis.account.domain;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Parent;

/**
 * An account's choice about AI features: which kind of LLM usage it consented to.
 * <p>
 * A row exists only for an account that has made a decision, which the database enforces. The absence of a row means "no
 * decision recorded" - resolve it through {@code UserAiPreferenceService} rather than reading this entity directly, so
 * that default lives in one place.
 */
@Entity
@Table(name = "user_ai_preference")
public class UserAiPreference {

    @Id
    @Column(name = "user_id")
    @Parent
    private long userId;

    /**
     * Which kind of LLM usage the account consented to. Required for a stored row; it is only null on a new instance
     * until the decision is set. {@code NO_AI} is a recorded decision to refuse, which is not the same as not having
     * decided.
     */
    @Nullable
    @Enumerated(EnumType.STRING)
    @Column(name = "selection_decision", length = 20, nullable = false)
    private AiSelectionDecision selectionDecision = null;

    /**
     * When the decision above was recorded, kept as evidence of consent.
     */
    @Nullable
    @Column(name = "selection_decision_date")
    private ZonedDateTime selectionDecisionDate = null;

    public UserAiPreference() {
        // needed by Hibernate
    }

    public UserAiPreference(long userId) {
        this.userId = userId;
    }

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    @Nullable
    public AiSelectionDecision getSelectionDecision() {
        return selectionDecision;
    }

    public void setSelectionDecision(@Nullable AiSelectionDecision selectionDecision) {
        this.selectionDecision = selectionDecision;
    }

    @Nullable
    public ZonedDateTime getSelectionDecisionDate() {
        return selectionDecisionDate;
    }

    public void setSelectionDecisionDate(@Nullable ZonedDateTime selectionDecisionDate) {
        this.selectionDecisionDate = selectionDecisionDate;
    }
}
