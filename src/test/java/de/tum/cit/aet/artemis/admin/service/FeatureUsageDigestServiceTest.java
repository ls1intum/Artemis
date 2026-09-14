package de.tum.cit.aet.artemis.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleCallsDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageModuleSummaryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;

/**
 * Tests the roll-up behind the weekly email.
 * <p>
 * The digest is built from the same report the admin page shows, so the risk is not in the numbers themselves but in the
 * aggregation: a module must not look half unused because of endpoints that no longer exist, and a module nobody touched
 * has to end up on the quiet list rather than in a row of zeros.
 */
class FeatureUsageDigestServiceTest {

    private static final LocalDate FROM = LocalDate.now(ZoneOffset.UTC).minusDays(6);

    /** Fixed rather than read per call, so a test can assert the exact retirement cutoff derived from it. */
    private static final Instant INVENTORY_REFRESHED_AT = Instant.now();

    private FeatureUsageQueryService queryService;

    private FeatureUsageStatisticsRepository repository;

    private FeatureUsageDigestService service;

    @BeforeEach
    void init() {
        queryService = mock(FeatureUsageQueryService.class);
        repository = mock(FeatureUsageStatisticsRepository.class);
        service = new FeatureUsageDigestService(queryService, repository);
        when(repository.findModuleCallsBetween(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldRollUpCallsAndFeatureCountsPerModule() {
        givenOverview(entry("programming", 100, 5, false), entry("programming", 50, 0, false), entry("programming", 0, 0, false), entry("quiz", 7, 0, false));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        FeatureUsageModuleSummaryDTO programming = moduleOf(digest, "programming");
        assertThat(programming.callCount()).isEqualTo(150);
        assertThat(programming.errorCount()).isEqualTo(5);
        assertThat(programming.usedFeatures()).isEqualTo(2);
        assertThat(programming.trackedFeatures()).isEqualTo(3);
        assertThat(programming.unusedFeatures()).isEqualTo(1);
    }

    /**
     * The digest counts features, not endpoints.
     * <p>
     * The admin page collapses endpoints that share a {@code @FeatureUsage} label into one row; the overview it is built
     * from counts inventory rows. Counting rows here made the email contradict the page it summarises, in the same way
     * the page itself used to ("895 unused" over 131 features). Three endpoints behind one label are one feature.
     */
    @Test
    void shouldCountLabelledEndpointsAsOneFeature() {
        givenOverview(labelledEntry("programming", "configuration/sca", 60, 1, false), labelledEntry("programming", "configuration/sca", 40, 0, false),
                labelledEntry("programming", "configuration/sca", 0, 0, false), entry("programming", 0, 0, false));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // One labelled feature plus one unlabelled endpoint, not four rows
        assertThat(digest.trackedFeatures()).isEqualTo(2);
        assertThat(digest.usedFeatures()).isEqualTo(1);
        assertThat(digest.unusedFeatures()).isEqualTo(1);
        FeatureUsageModuleSummaryDTO programming = moduleOf(digest, "programming");
        assertThat(programming.trackedFeatures()).isEqualTo(2);
        assertThat(programming.usedFeatures()).isEqualTo(1);
        assertThat(programming.unusedFeatures()).isEqualTo(1);
        // The calls of every endpoint behind the label still count once each
        assertThat(programming.callCount()).isEqualTo(100);
        assertThat(programming.errorCount()).isEqualTo(1);
    }

    /**
     * A label is retired only once every endpoint behind it is gone, which is the rule the page applies. While one
     * endpoint remains, the feature still exists and must keep counting as offered.
     */
    @Test
    void shouldTreatALabelAsRetiredOnlyWhenEveryEndpointBehindItIsGone() {
        givenOverview(labelledEntry("programming", "configuration/sca", 5, 0, false), labelledEntry("programming", "configuration/sca", 0, 0, true),
                labelledEntry("lti", "configuration/lti", 0, 0, true), labelledEntry("lti", "configuration/lti", 0, 0, true));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // sca survives because one endpoint remains; lti is gone entirely
        assertThat(digest.trackedFeatures()).isEqualTo(1);
        assertThat(digest.usedFeatures()).isEqualTo(1);
        assertThat(digest.unusedFeatures()).isZero();
        assertThat(digest.retiredFeatures()).isEqualTo(1);
    }

    @Test
    void shouldOrderActiveModulesByCalls() {
        givenOverview(entry("quiz", 7, 0, false), entry("programming", 100, 0, false), entry("exam", 40, 0, false));

        assertThat(service.buildWeeklyDigest().activeModules()).extracting(FeatureUsageModuleSummaryDTO::module).containsExactly("programming", "exam", "quiz");
    }

    @Test
    void shouldListModulesWithoutAnyUsageSeparately() {
        givenOverview(entry("programming", 100, 0, false), entry("lecture", 0, 0, false), entry("quiz", 0, 0, false));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // what matters about these is only that they are on the list, so they are names rather than rows of zeros
        assertThat(digest.quietModules()).containsExactly("lecture", "quiz");
        assertThat(digest.activeModules()).extracting(FeatureUsageModuleSummaryDTO::module).containsExactly("programming");
    }

    @Test
    void shouldExcludeRetiredFeaturesFromEveryModuleCount() {
        givenOverview(entry("programming", 100, 0, false), entry("programming", 0, 0, true), entry("programming", 0, 0, true));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // a module is not two thirds unused because it stopped offering two endpoints a release ago
        FeatureUsageModuleSummaryDTO programming = moduleOf(digest, "programming");
        assertThat(programming.trackedFeatures()).isEqualTo(1);
        assertThat(programming.unusedFeatures()).isZero();
        assertThat(digest.trackedFeatures()).isEqualTo(1);
        assertThat(digest.usedFeatures()).isEqualTo(1);
    }

    @Test
    void shouldDropAModuleThatOnlyHasRetiredFeatures() {
        givenOverview(entry("programming", 100, 0, false), entry("lti", 0, 0, true));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        assertThat(digest.quietModules()).isEmpty();
        assertThat(digest.activeModules()).extracting(FeatureUsageModuleSummaryDTO::module).containsExactly("programming");
    }

    @Test
    void shouldCompareAgainstTheEquallyLongWindowBefore() {
        givenOverview(entry("programming", 150, 0, false));
        when(repository.findModuleCallsBetween(eq(FROM.minusDays(7)), eq(FROM.minusDays(1)), any())).thenReturn(List.of(new FeatureUsageModuleCallsDTO("programming", 100)));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        assertThat(moduleOf(digest, "programming").previousCallCount()).isEqualTo(100);
        assertThat(moduleOf(digest, "programming").changePercent()).isEqualTo(50);
        assertThat(digest.previousTotalCalls()).isEqualTo(100);
    }

    @Test
    void shouldReportNoChangeWhenThereIsNothingToCompareAgainst() {
        givenOverview(entry("programming", 150, 0, false));

        // "no previous data" and "no change" are different things in a digest, so the absence has to stay distinguishable
        assertThat(moduleOf(service.buildWeeklyDigest(), "programming").changePercent()).isNull();
    }

    @Test
    void shouldReportADrop() {
        givenOverview(entry("exam", 20, 0, false));
        when(repository.findModuleCallsBetween(any(), any(), any())).thenReturn(List.of(new FeatureUsageModuleCallsDTO("exam", 100)));

        assertThat(moduleOf(service.buildWeeklyDigest(), "exam").changePercent()).isEqualTo(-80);
    }

    @Test
    void shouldBeEmptyWhenNothingWasRecorded() {
        givenOverview(entry("programming", 0, 0, false));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // the template says so explicitly instead of presenting a table of zeros as a finding
        assertThat(digest.isEmpty()).isTrue();
    }

    @Test
    void shouldCoverExactlyOneWeek() {
        givenOverview(entry("programming", 1, 0, false));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        assertThat(digest.days()).isEqualTo(7);
        assertThat(digest.from()).isEqualTo(FROM);
        assertThat(digest.to()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * The email states that entries this version no longer offers are excluded from its counts, so both the headline and
     * the comparison have to be taken over the same still-offered set as the module rows. Otherwise a feature removed
     * between the two windows keeps contributing to the earlier one and reads as a usage drop that never happened, and the
     * headline disagrees with the sum of the rows beneath it.
     */
    @Test
    void shouldExcludeRetiredFeaturesFromTheHeadlineAndTheComparison() {
        givenOverview(entry("programming", 150, 0, false), entry("programming", 400, 0, true));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // 400 belongs to an endpoint this version no longer offers, so it counts towards neither the headline nor the row
        assertThat(digest.totalCalls()).isEqualTo(150);
        assertThat(moduleOf(digest, "programming").callCount()).isEqualTo(150);
        assertThat(digest.retiredFeatures()).isEqualTo(1);
    }

    /**
     * A label that covers both a live and a removed endpoint is the case where "does the feature still exist" and "what
     * did it get used for" have different answers. The label is still offered while one endpoint remains, but the removed
     * endpoint's calls must not be counted, because the previous window is queried with retired rows filtered out one by
     * one. Counting them on only one side reports a week-over-week change that never happened.
     */
    @Test
    void shouldExcludeARetiredEndpointOfAStillOfferedFeatureFromItsCounters() {
        givenOverview(labelledEntry("programming", "authoring/exercise-management", 150, 1, false), labelledEntry("programming", "authoring/exercise-management", 400, 9, true));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // one endpoint remains, so this is one still-offered feature rather than a retired one
        assertThat(digest.trackedFeatures()).isEqualTo(1);
        assertThat(digest.retiredFeatures()).isZero();
        // but the endpoint this version no longer offers contributes neither its calls nor its errors
        assertThat(digest.totalCalls()).isEqualTo(150);
        assertThat(moduleOf(digest, "programming").callCount()).isEqualTo(150);
        assertThat(moduleOf(digest, "programming").errorCount()).isEqualTo(1);
    }

    @Test
    void shouldAskForThePreviousWindowWithTheSameRetirementCutoffAsTheReport() {
        givenOverview(entry("programming", 150, 0, false));

        service.buildWeeklyDigest();

        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(repository).findModuleCallsBetween(eq(FROM.minusDays(7)), eq(FROM.minusDays(1)), cutoff.capture());
        // the same cutoff the report applies, so the two windows and the page cannot classify a feature differently
        assertThat(cutoff.getValue()).isEqualTo(FeatureUsageQueryService.retirementCutoff(INVENTORY_REFRESHED_AT));
    }

    private void givenOverview(FeatureUsageEntryDTO... entries) {
        List<FeatureUsageEntryDTO> features = List.of(entries);
        long unused = features.stream().filter(feature -> !feature.retired() && feature.callCount() == 0).count();
        long retired = features.stream().filter(FeatureUsageEntryDTO::retired).count();
        long total = features.stream().mapToLong(FeatureUsageEntryDTO::callCount).sum();
        when(queryService.getOverview(anyInt(), any())).thenReturn(new FeatureUsageOverviewDTO(7, FROM, null, features.size(), unused, retired, total, INVENTORY_REFRESHED_AT,
                INVENTORY_REFRESHED_AT, features, List.of(), List.of()));
    }

    private static FeatureUsageEntryDTO labelledEntry(String module, String featureLabel, long callCount, long errorCount, boolean retired) {
        return new FeatureUsageEntryDTO(0, FeatureKind.REST, module, "GET api/" + module + "/" + featureLabel + "-" + callCount + "-" + retired, featureLabel, callCount,
                errorCount, 0, 0, 0, null, Instant.now(), retired);
    }

    private static FeatureUsageEntryDTO entry(String module, long callCount, long errorCount, boolean retired) {
        return new FeatureUsageEntryDTO(0, FeatureKind.REST, module, "GET api/" + module + "/" + callCount + "-" + retired, null, callCount, errorCount, 0, 0, 0, null,
                Instant.now(), retired);
    }

    private static FeatureUsageModuleSummaryDTO moduleOf(FeatureUsageDigestDTO digest, String module) {
        return digest.activeModules().stream().filter(summary -> summary.module().equals(module)).findFirst().orElseThrow();
    }
}
