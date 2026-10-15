package de.tum.cit.aet.artemis.communication.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqCensusEntryDTO;
import de.tum.cit.aet.artemis.communication.dto.FaqFingerprintInputDTO;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.api.PyrisFaqApi;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;

/**
 * Keeps the FAQs Iris can answer with in line with the accepted FAQs in Artemis.
 * <p>
 * Artemis pushes an FAQ to Iris when it is created or edited, and deletes it when it is deleted. A push or delete that fails, or a write
 * that Iris loses (for example when its vector store is replaced), is never retried by that path. This job compares the Pyris FAQ census of
 * this instance with the accepted FAQs of every course with Iris enabled, and pushes FAQs that are missing, changed, duplicated or still
 * stored as legacy rows, and deletes rows of FAQs that no longer exist or are not accepted anymore. Legacy rows (written before FAQ rows
 * carried an instance) are only replaced by a push, never deleted as orphans, because another Artemis instance sharing the Pyris may own
 * them. Courses with Iris disabled are left alone.
 * <p>
 * Each run spends at most {@code artemis.iris.faq-reconcile.max-actions-per-run} pushes and deletes, because each starts an Iris job with
 * an embedding call. A failed action costs its share of the budget like a successful one, and the next run continues after the last action
 * attempted, so an FAQ that keeps failing never blocks the others. The position lives in memory: the job runs on the scheduling node only,
 * and losing the position on a restart only restarts the order.
 */
@Conditional(IrisEnabled.class)
@Profile(PROFILE_SCHEDULING)
@Lazy
@Service
public class FaqIngestionReconcileService {

    private static final Logger log = LoggerFactory.getLogger(FaqIngestionReconcileService.class);

    /** Pushes of the same FAQ without the census ever matching, after which it is reported, since its ingestion keeps failing in Iris. */
    static final int REPEATED_PUSH_WARNING_THRESHOLD = 5;

    private final FaqRepository faqRepository;

    private final Optional<PyrisFaqApi> pyrisFaqApi;

    private final Optional<IrisSettingsApi> irisSettingsApi;

    private final int maxActionsPerRun;

    /** The last action attempted, so the next run continues after it; {@code null} starts at the beginning. */
    @Nullable
    private ActionKey lastAttempted;

    /** Pushes per FAQ since the census last matched it. */
    private final Map<Long, Integer> pushesSinceMatch = new ConcurrentHashMap<>();

    public FaqIngestionReconcileService(FaqRepository faqRepository, Optional<PyrisFaqApi> pyrisFaqApi, Optional<IrisSettingsApi> irisSettingsApi,
            @Value("${artemis.iris.faq-reconcile.max-actions-per-run:20}") int maxActionsPerRun) {
        this.faqRepository = faqRepository;
        this.pyrisFaqApi = pyrisFaqApi;
        this.irisSettingsApi = irisSettingsApi;
        this.maxActionsPerRun = maxActionsPerRun;
    }

    /**
     * Run one reconciliation pass on the configured schedule.
     */
    @Scheduled(cron = "${artemis.scheduling.faq-ingestion-reconcile-time:0 */10 * * * *}")
    public void scheduledReconcile() {
        reconcile();
    }

    /**
     * Compare the census with the accepted FAQs and push or delete within the budget.
     *
     * @return how many pushes and deletes were attempted
     */
    int reconcile() {
        if (pyrisFaqApi.isEmpty() || irisSettingsApi.isEmpty()) {
            return 0;
        }
        FaqCensusDTO census = pyrisFaqApi.get().getFaqCensus();
        if (census == null) {
            return 0;
        }
        List<Action> actions = planActions(census);
        if (actions.isEmpty()) {
            return 0;
        }
        int attempted = 0;
        int failed = 0;
        for (Action action : inRunOrder(actions)) {
            if (attempted >= maxActionsPerRun) {
                break;
            }
            attempted++;
            lastAttempted = action.key();
            if (!execute(action)) {
                failed++;
            }
        }
        log.info("FAQ reconcile: {} of {} pending pushes and deletes attempted, {} failed", attempted, actions.size(), failed);
        return attempted;
    }

    /**
     * The pushes and deletes that bring Iris in line with Artemis, sorted by course and FAQ.
     */
    private List<Action> planActions(FaqCensusDTO census) {
        List<FaqFingerprintInputDTO> accepted = faqRepository.findFingerprintInputsByFaqState(FaqState.ACCEPTED);
        Set<Long> courseIds = Stream.concat(accepted.stream().map(FaqFingerprintInputDTO::courseId), census.faqs().stream().map(FaqCensusEntryDTO::courseId))
                .collect(Collectors.toSet());
        Set<Long> irisEnabled = new HashSet<>(irisSettingsApi.orElseThrow().filterCourseIdsWithIrisEnabled(courseIds));

        Map<ActionKey, FaqCensusEntryDTO> censusByKey = new HashMap<>();
        for (FaqCensusEntryDTO entry : census.faqs()) {
            censusByKey.put(new ActionKey(entry.courseId(), entry.faqId()), entry);
        }

        List<Action> actions = new ArrayList<>();
        Set<ActionKey> expected = new HashSet<>();
        for (FaqFingerprintInputDTO faq : accepted) {
            if (!irisEnabled.contains(faq.courseId())) {
                continue;
            }
            ActionKey key = new ActionKey(faq.courseId(), faq.faqId());
            expected.add(key);
            FaqCensusEntryDTO entry = censusByKey.get(key);
            if (needsPush(entry, Faq.contentFingerprint(faq.questionTitle(), faq.questionAnswer()))) {
                actions.add(new Action(key, ActionType.PUSH));
            }
            else {
                pushesSinceMatch.remove(faq.faqId());
            }
        }
        for (FaqCensusEntryDTO entry : census.faqs()) {
            ActionKey key = new ActionKey(entry.courseId(), entry.faqId());
            // Only rows of this instance are deleted; legacy rows of an unknown owner stay, as they did before FAQ rows carried an instance
            if (entry.ownedRowCount() > 0 && irisEnabled.contains(entry.courseId()) && !expected.contains(key)) {
                actions.add(new Action(key, ActionType.DELETE));
            }
        }
        actions.sort(Comparator.comparing(Action::key));
        return actions;
    }

    /**
     * Whether Iris must be sent the FAQ again: no row of this instance, duplicates, a different or missing fingerprint, or a legacy row that
     * the push replaces.
     */
    private static boolean needsPush(@Nullable FaqCensusEntryDTO entry, String fingerprint) {
        return entry == null || entry.ownedRowCount() != 1 || !fingerprint.equals(entry.ownedFingerprint()) || entry.legacyRowCount() > 0;
    }

    /**
     * The actions starting after the last one attempted, wrapping around.
     */
    private List<Action> inRunOrder(List<Action> actions) {
        if (lastAttempted == null) {
            return actions;
        }
        int start = 0;
        while (start < actions.size() && actions.get(start).key().compareTo(lastAttempted) <= 0) {
            start++;
        }
        List<Action> ordered = new ArrayList<>(actions.subList(start, actions.size()));
        ordered.addAll(actions.subList(0, start));
        return ordered;
    }

    /**
     * Run one action, isolated from the others.
     *
     * @return whether it was handed to Pyris
     */
    private boolean execute(Action action) {
        try {
            if (action.type() == ActionType.DELETE) {
                pyrisFaqApi.orElseThrow().deleteFaq(action.key().faqId(), action.key().courseId());
                return true;
            }
            Optional<Faq> faq = faqRepository.findById(action.key().faqId());
            if (faq.isEmpty() || faq.get().getFaqState() != FaqState.ACCEPTED) {
                // Changed since the census was compared; the next run decides again
                return true;
            }
            int pushes = pushesSinceMatch.merge(action.key().faqId(), 1, Integer::sum);
            if (pushes == REPEATED_PUSH_WARNING_THRESHOLD) {
                log.warn("FAQ reconcile: FAQ {} of course {} was pushed {} times without Iris storing it; its ingestion keeps failing in Iris", action.key().faqId(),
                        action.key().courseId(), pushes);
            }
            pyrisFaqApi.orElseThrow().addFaq(faq.get());
            return true;
        }
        catch (RuntimeException e) {
            log.warn("FAQ reconcile: {} of FAQ {} in course {} failed: {}", action.type(), action.key().faqId(), action.key().courseId(), e.getMessage());
            return false;
        }
    }

    private enum ActionType {
        PUSH, DELETE
    }

    private record ActionKey(long courseId, long faqId) implements Comparable<ActionKey> {

        @Override
        public int compareTo(ActionKey other) {
            int byCourse = Long.compare(courseId, other.courseId);
            return byCourse != 0 ? byCourse : Long.compare(faqId, other.faqId);
        }
    }

    private record Action(ActionKey key, ActionType type) {
    }
}
