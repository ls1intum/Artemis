package de.tum.cit.aet.artemis.account.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.UserAiPreference;
import de.tum.cit.aet.artemis.account.repository.UserAiPreferenceRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;

/**
 * Owns an account's AI preferences, which live in {@code user_ai_preference} rather than on the user row.
 * <p>
 * A row exists only for an account that has made a decision or turned Memiris off, so this service is the single place
 * that turns "no row" into the defaults: no decision recorded, and Memiris enabled. Handing callers the entity or a bare
 * {@code Optional} would spread that default across every reader, and getting it wrong flips behaviour for the majority
 * of accounts, which have no row.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserAiPreferenceService {

    private final UserAiPreferenceRepository userAiPreferenceRepository;

    public UserAiPreferenceService(UserAiPreferenceRepository userAiPreferenceRepository) {
        this.userAiPreferenceRepository = userAiPreferenceRepository;
    }

    /**
     * The account's recorded LLM usage decision, or null when it has not made one.
     *
     * @param userId the account
     * @return the decision, or null
     */
    @Nullable
    public AiSelectionDecision findDecision(long userId) {
        return userAiPreferenceRepository.findByUserId(userId).map(UserAiPreference::getSelectionDecision).orElse(null);
    }

    /**
     * When the account recorded its decision, or null when it has not made one.
     *
     * @param userId the account
     * @return the timestamp, or null
     */
    @Nullable
    public ZonedDateTime findDecisionDate(long userId) {
        return userAiPreferenceRepository.findByUserId(userId).map(UserAiPreference::getSelectionDecisionDate).orElse(null);
    }

    /**
     * The decisions of several accounts in one query, for callers that would otherwise read one per item - assembling a
     * post together with its answers, for instance. Accounts without a recorded decision are absent from the result
     * rather than mapped to null, so callers can treat a missing key and a null value alike.
     *
     * @param userIds the accounts to look up
     * @return the recorded decisions by account id
     */
    public Map<Long, AiSelectionDecision> findDecisions(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinct = userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userAiPreferenceRepository.findAllByUserIdIn(distinct).stream().filter(preference -> preference.getSelectionDecision() != null)
                .collect(Collectors.toMap(UserAiPreference::getUserId, UserAiPreference::getSelectionDecision));
    }

    /**
     * Whether the account has a preference row at all, which is not the same as having decided: {@link #clearDecision}
     * leaves the row behind when it still carries a Memiris choice. Callers that need to tell "never recorded anything"
     * from "decision cleared" have to ask this rather than compare the decision against null.
     *
     * @param userId the account
     * @return true if a row exists
     */
    public boolean hasPreferenceRow(long userId) {
        return userAiPreferenceRepository.findByUserId(userId).isPresent();
    }

    /**
     * Whether Memiris may remember anything about the account. An account with no row has it enabled, which is what the
     * column default meant.
     *
     * @param userId the account
     * @return true unless the account has explicitly turned it off
     */
    public boolean isMemirisEnabled(long userId) {
        return userAiPreferenceRepository.findByUserId(userId).map(UserAiPreference::isMemirisEnabled).orElse(true);
    }

    /**
     * Whether the account has opted into LLM usage, meaning it recorded a decision other than {@code NO_AI}.
     *
     * @param userId the account
     * @return true if AI features may be used for it
     */
    public boolean hasOptedIntoLlmUsage(long userId) {
        AiSelectionDecision decision = findDecision(userId);
        return decision != null && decision != AiSelectionDecision.NO_AI;
    }

    /**
     * Throws unless the account has opted into LLM usage.
     * <p>
     * The decision is read from the preference row, which is the only place that holds it: a guard that consults the user
     * entity instead has nothing to read and would reject every account.
     *
     * @param userId the account
     * @throws AccessForbiddenException if the account has not opted in
     */
    public void hasOptedIntoLlmUsageElseThrow(long userId) {
        if (!hasOptedIntoLlmUsage(userId)) {
            throw new AccessForbiddenException("The user has not selected to use AI.");
        }
    }

    /**
     * Records the account's LLM usage decision.
     *
     * @param userId   the account
     * @param decision what it chose
     * @param when     when it chose
     */
    public void recordDecision(long userId, AiSelectionDecision decision, ZonedDateTime when) {
        saveHandlingConcurrentInsert(userId, preference -> {
            preference.setSelectionDecision(decision);
            preference.setSelectionDecisionDate(when);
        });
    }

    /**
     * Turns Memiris on or off for the account.
     *
     * @param userId         the account
     * @param memirisEnabled whether Memiris may remember anything
     */
    public void setMemirisEnabled(long userId, boolean memirisEnabled) {
        saveHandlingConcurrentInsert(userId, preference -> preference.setMemirisEnabled(memirisEnabled));
    }

    /**
     * Removes the account's recorded decision, leaving it as an account that has not decided yet. The row is kept if it
     * still carries a Memiris choice, and removed when nothing is left.
     *
     * @param userId the account
     */
    public void clearDecision(long userId) {
        userAiPreferenceRepository.findByUserId(userId).ifPresent(preference -> {
            preference.setSelectionDecision(null);
            preference.setSelectionDecisionDate(null);
            if (preference.isMemirisEnabled()) {
                userAiPreferenceRepository.delete(preference);
            }
            else {
                userAiPreferenceRepository.save(preference);
            }
        });
    }

    /**
     * Saves a row that may have been created concurrently.
     * <p>
     * These rows are keyed on the user id, so two first writes for the same account both find nothing and both insert.
     * The loser gets a primary-key violation, which would surface as an error on a request that did nothing wrong. It
     * reloads instead and applies the change to the row the winner created.
     *
     * @param userId   the account
     * @param mutation the change to apply
     */
    private void saveHandlingConcurrentInsert(long userId, Consumer<UserAiPreference> mutation) {
        UserAiPreference row = userAiPreferenceRepository.findByUserId(userId).orElseGet(() -> new UserAiPreference(userId));
        mutation.accept(row);
        try {
            userAiPreferenceRepository.save(row);
        }
        catch (DataIntegrityViolationException concurrentInsert) {
            UserAiPreference created = userAiPreferenceRepository.findByUserId(userId).orElseThrow(() -> concurrentInsert);
            mutation.accept(created);
            userAiPreferenceRepository.save(created);
        }
    }
}
