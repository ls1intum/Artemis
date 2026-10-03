package de.tum.cit.aet.artemis.globalsearch.service.reconcile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.globalsearch.config.WeaviateReconcileProperties;
import de.tum.cit.aet.artemis.globalsearch.domain.ReconcilePass;

class SearchableEntityReconcileSchedulerTest {

    private static final List<String> DEFAULT_TYPES = List.of("course", "lecture", "lecture_unit", "exam", "exercise", "faq", "channel");

    private final SearchableEntityMissingSweep missingSweep = mock(SearchableEntityMissingSweep.class);

    private final SearchableEntityDriftSweep driftSweep = mock(SearchableEntityDriftSweep.class);

    private final SearchableEntityOrphanSweep orphanSweep = mock(SearchableEntityOrphanSweep.class);

    private static WeaviateReconcileProperties properties() {
        // The individual *SweepEnabled flags only seed the runtime toggle's first-ever value (FeatureToggleService),
        // so they play no role at scheduling time; what runs is decided by FeatureToggleService.isFeatureEnabled.
        return new WeaviateReconcileProperties(false, false, false, DEFAULT_TYPES, 500, 100, 200, 1000, 5, 100, 100, 0.25);
    }

    private SearchableEntityReconcileScheduler scheduler(FeatureToggleService featureToggleService) {
        return new SearchableEntityReconcileScheduler(properties(), featureToggleService, missingSweep, driftSweep, orphanSweep);
    }

    @Test
    void testDisabledPassDoesNotRunItsBody() {
        var scheduler = scheduler(mock(FeatureToggleService.class));
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.runPass(ReconcilePass.DRIFT, false, () -> ran.set(true));

        assertThat(ran).isFalse();
    }

    @Test
    void testEnabledPassRunsItsBody() {
        var scheduler = scheduler(mock(FeatureToggleService.class));
        AtomicBoolean ran = new AtomicBoolean(false);

        scheduler.runPass(ReconcilePass.DRIFT, true, () -> ran.set(true));

        assertThat(ran).isTrue();
    }

    @Test
    void testFailingPassDoesNotPropagate() {
        // A scheduled method that throws is suppressed from further executions, which would silently retire the
        // pass until the next restart. A transient failure has to stay transient.
        var scheduler = scheduler(mock(FeatureToggleService.class));

        assertThatCode(() -> scheduler.runPass(ReconcilePass.ORPHAN, true, () -> {
            throw new IllegalStateException("Weaviate unreachable");
        })).doesNotThrowAnyException();
    }

    @Test
    void testScheduledEntryPointsAreNoOpsWhileTheirToggleIsOff() {
        // A plain mock answers false for every unstubbed boolean method, matching a system where neither toggle
        // has ever been switched on.
        var scheduler = scheduler(mock(FeatureToggleService.class));

        assertThatCode(() -> {
            scheduler.reconcileDrift();
            scheduler.reconcileMissing();
            scheduler.reconcileOrphans();
            scheduler.logEffectiveConfiguration();
        }).doesNotThrowAnyException();

        verifyNoInteractions(missingSweep, driftSweep, orphanSweep);
    }

    @Test
    void testMissingAndDriftRunOnTheCombinedToggleWithOrphanStillOff() {
        // Neither missing nor drift deletes, so they share Feature.GlobalSearchReconcile; orphan is the one pass
        // that deletes and is gated by its own Feature.GlobalSearchReconcileOrphan, switched on separately. A mock
        // answers false for the un-stubbed orphan toggle, matching an operator who enabled only the safer half.
        var featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isFeatureEnabled(Feature.GlobalSearchReconcile)).thenReturn(true);
        var scheduler = scheduler(featureToggleService);

        scheduler.reconcileDrift();
        scheduler.reconcileMissing();
        scheduler.reconcileOrphans();

        verify(driftSweep).sweep();
        verify(missingSweep).sweep();
        verifyNoInteractions(orphanSweep);
    }

    @Test
    void testOrphanRunsOnlyOnItsOwnToggleEvenWithTheCombinedOneOff() {
        var featureToggleService = mock(FeatureToggleService.class);
        when(featureToggleService.isFeatureEnabled(Feature.GlobalSearchReconcileOrphan)).thenReturn(true);
        var scheduler = scheduler(featureToggleService);

        scheduler.reconcileDrift();
        scheduler.reconcileMissing();
        scheduler.reconcileOrphans();

        verifyNoInteractions(driftSweep, missingSweep);
        verify(orphanSweep).sweep();
    }

    @Test
    void testPostsAreNotManagedByDefault() {
        // Posts and answer posts dominate the corpus, and the passes share a budget. Including them would stretch
        // every other type's revisit period to match theirs.
        var defaults = properties();

        assertThat(defaults.managesEntityType("course")).isTrue();
        assertThat(defaults.managesEntityType("post")).isFalse();
        assertThat(defaults.managesEntityType("answer_post")).isFalse();
    }
}
