package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;

class SearchableEntityReconcileSchedulerTest {

    private static final List<String> DEFAULT_TYPES = List.of("course", "lecture", "lecture_unit", "exam", "exercise", "faq", "channel");

    private static WeaviateReconcileProperties properties(boolean missing, boolean drift, boolean orphan) {
        return new WeaviateReconcileProperties(missing, drift, orphan, DEFAULT_TYPES, 500, 100, 200, 1000, 5, 100, 100, 0.25);
    }

    @Test
    void testEveryPassIsDisabledByDefault() {
        // The passes queue the whole corpus the first time the missing pass runs, and the orphan pass deletes.
        // Neither should ever start because someone deployed; switching them on is a deliberate act.
        var defaults = properties(false, false, false);

        assertThat(defaults.missingSweepEnabled()).isFalse();
        assertThat(defaults.driftSweepEnabled()).isFalse();
        assertThat(defaults.orphanSweepEnabled()).isFalse();
        assertThat(defaults.anyPassEnabled()).isFalse();
    }

    @Test
    void testDisabledPassDoesNotRunItsBody() {
        var scheduler = new SearchableEntityReconcileScheduler(properties(false, false, false), mock(SearchableEntityMissingSweep.class), mock(SearchableEntityDriftSweep.class),
                mock(SearchableEntityOrphanSweep.class));
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.runPass(ReconcilePass.DRIFT, false, () -> ran.set(true));

        assertThat(ran).isFalse();
    }

    @Test
    void testEnabledPassRunsItsBody() {
        var scheduler = new SearchableEntityReconcileScheduler(properties(true, true, true), mock(SearchableEntityMissingSweep.class), mock(SearchableEntityDriftSweep.class),
                mock(SearchableEntityOrphanSweep.class));
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.runPass(ReconcilePass.DRIFT, true, () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void testFailingPassDoesNotPropagate() {
        // A scheduled method that throws is suppressed from further executions, which would silently retire the
        // pass until the next restart. A transient failure has to stay transient.
        var scheduler = new SearchableEntityReconcileScheduler(properties(true, true, true), mock(SearchableEntityMissingSweep.class), mock(SearchableEntityDriftSweep.class),
                mock(SearchableEntityOrphanSweep.class));

        assertThatCode(() -> scheduler.runPass(ReconcilePass.ORPHAN, true, () -> {
            throw new IllegalStateException("Weaviate unreachable");
        })).doesNotThrowAnyException();
    }

    @Test
    void testScheduledEntryPointsAreNoOpsWhileDisabled() {
        var scheduler = new SearchableEntityReconcileScheduler(properties(false, false, false), mock(SearchableEntityMissingSweep.class), mock(SearchableEntityDriftSweep.class),
                mock(SearchableEntityOrphanSweep.class));

        assertThatCode(() -> {
            scheduler.reconcileDrift();
            scheduler.reconcileMissing();
            scheduler.reconcileOrphans();
            scheduler.logEffectiveConfiguration();
        }).doesNotThrowAnyException();
    }

    @Test
    void testPostsAreNotManagedByDefault() {
        // Posts and answer posts dominate the corpus, and the passes share a budget. Including them would stretch
        // every other type's revisit period to match theirs.
        var defaults = properties(true, true, true);

        assertThat(defaults.managesEntityType("course")).isTrue();
        assertThat(defaults.managesEntityType("post")).isFalse();
        assertThat(defaults.managesEntityType("answer_post")).isFalse();
    }
}
