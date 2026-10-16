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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.admin.domain.FeatureUsageStatus;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageAreaSummaryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageDigestDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageLabelCallsDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.UserFeatureUsageDTO;
import de.tum.cit.aet.artemis.admin.repository.FeatureUsageStatisticsRepository;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;

/**
 * Tests the roll-up behind the weekly email.
 * <p>
 * The digest is built from the same report the admin page shows, so the risk is not in the numbers themselves but in the
 * aggregation: automatic calls must not make an area look used, an area nobody used has to end up on the quiet list
 * rather than in a row of zeros, and an area this deployment does not offer must not appear at all.
 */
class FeatureUsageDigestServiceTest {

    private static final LocalDate FROM = LocalDate.now(ZoneOffset.UTC).minusDays(6);

    private FeatureUsageQueryService queryService;

    private FeatureUsageStatisticsRepository repository;

    private FeatureUsageDigestService service;

    @BeforeEach
    void init() {
        queryService = mock(FeatureUsageQueryService.class);
        repository = mock(FeatureUsageStatisticsRepository.class);
        service = new FeatureUsageDigestService(queryService, repository);
        when(repository.findFeatureCallsBetween(any(), any())).thenReturn(List.of());
    }

    @Test
    void shouldRollUpUseAndFeatureCountsPerArea() {
        givenOverview(Map.of(UserFeature.PROGRAMMING_ONLINE_EDITOR, used(100, 50, 5), UserFeature.PROGRAMMING_RESULTS, used(0, 30, 1), UserFeature.PROGRAMMING_ONLINE_IDE, unused(),
                UserFeature.PROGRAMMING_REPOSITORY_HISTORY, onlyAutomatic(400)));

        FeatureUsageAreaSummaryDTO programming = areaOf(service.buildWeeklyDigest(), ProductArea.PROGRAMMING);

        // actions and views only, the 400 automatic calls are not use
        assertThat(programming.useCount()).isEqualTo(180);
        assertThat(programming.actionCount()).isEqualTo(100);
        assertThat(programming.errorCount()).isEqualTo(6);
        assertThat(programming.usedFeatures()).isEqualTo(2);
        assertThat(programming.availableFeatures()).isEqualTo(4);
        assertThat(programming.unusedFeatures()).isEqualTo(2);
        assertThat(programming.onlyAutomatic()).isEqualTo(1);
    }

    @Test
    void shouldCountFeaturesThatOnlyReceivedAutomaticCallsAsUnused() {
        givenOverview(Map.of(UserFeature.HYPERION_VARIANT_GENERATION, onlyAutomatic(700), UserFeature.HYPERION_CODE_GENERATION, unused(), UserFeature.FAQ, used(0, 3, 0)));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        assertThat(digest.useCount()).isEqualTo(3);
        assertThat(digest.usedFeatures()).isEqualTo(1);
        assertThat(digest.unusedFeatures()).isEqualTo(2);
        assertThat(digest.onlyAutomatic()).isEqualTo(1);
        // an area that only saw automatic calls was not used, so it is quiet
        assertThat(digest.quietAreas()).containsExactly(ProductArea.AI_AUTHORING);
    }

    @Test
    void shouldOrderActiveAreasByUse() {
        givenOverview(Map.of(UserFeature.QUIZ_LIVE, used(5, 0, 0), UserFeature.PROGRAMMING_ONLINE_EDITOR, used(100, 0, 0), UserFeature.FAQ, used(0, 20, 0)));

        assertThat(service.buildWeeklyDigest().activeAreas()).extracting(FeatureUsageAreaSummaryDTO::area).containsExactly(ProductArea.PROGRAMMING, ProductArea.COMMUNICATION,
                ProductArea.QUIZ);
    }

    @Test
    void shouldLeaveOutAreasThisDeploymentDoesNotOffer() {
        // every other feature is not available, so their areas must appear neither as active nor as quiet
        givenOverview(Map.of(UserFeature.QUIZ_LIVE, used(5, 0, 0), UserFeature.DEIMOS, unused()));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        assertThat(digest.activeAreas()).extracting(FeatureUsageAreaSummaryDTO::area).containsExactly(ProductArea.QUIZ);
        assertThat(digest.quietAreas()).containsExactly(ProductArea.INTEGRITY);
        assertThat(digest.availableFeatures()).isEqualTo(2);
    }

    @Test
    void shouldCompareAgainstTheEquallyLongWindowBeforeCountingOnlyUse() {
        givenOverview(Map.of(UserFeature.PROGRAMMING_ONLINE_EDITOR, used(150, 0, 0)));
        when(repository.findFeatureCallsBetween(any(), any())).thenReturn(List.of(
                new FeatureUsageLabelCallsDTO(UserFeature.PROGRAMMING_ONLINE_EDITOR.name(), FeatureInteraction.ACTION, 80),
                new FeatureUsageLabelCallsDTO(UserFeature.PROGRAMMING_RESULTS.name(), FeatureInteraction.VIEW, 20),
                // neither an automatic call nor a row that belongs to no feature counts, on either side of the comparison
                new FeatureUsageLabelCallsDTO(UserFeature.PROGRAMMING_RESULTS.name(), FeatureInteraction.AUTOMATIC, 999),
                new FeatureUsageLabelCallsDTO("configuration/old-label", FeatureInteraction.ACTION, 999), new FeatureUsageLabelCallsDTO(null, FeatureInteraction.VIEW, 999)));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        FeatureUsageAreaSummaryDTO programming = areaOf(digest, ProductArea.PROGRAMMING);
        assertThat(programming.previousUseCount()).isEqualTo(100);
        assertThat(programming.changePercent()).isEqualTo(50);
        assertThat(digest.previousUseCount()).isEqualTo(100);
        verify(repository).findFeatureCallsBetween(eq(FROM.minusDays(7)), eq(FROM.minusDays(1)));
    }

    /**
     * An area that is not offered any more but was used in the previous week has to appear, otherwise the headline's drop
     * would be explained by no row.
     */
    @Test
    void shouldReportAnAreaThatWasUsedLastWeekAndIsNoLongerOffered() {
        givenOverview(Map.of(UserFeature.QUIZ_LIVE, used(5, 0, 0)));
        when(repository.findFeatureCallsBetween(any(), any())).thenReturn(List.of(new FeatureUsageLabelCallsDTO(UserFeature.SCIENCE.name(), FeatureInteraction.ACTION, 40)));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // not listed as quiet, because it offers nothing to use any more, but its drop is visible as a row
        assertThat(digest.quietAreas()).doesNotContain(ProductArea.COMPETENCIES);
        FeatureUsageAreaSummaryDTO competencies = areaOf(digest, ProductArea.COMPETENCIES);
        assertThat(competencies.useCount()).isZero();
        assertThat(competencies.changePercent()).isEqualTo(-100);
        assertThat(digest.previousUseCount()).isEqualTo(40);
        assertThat(digest.useCount()).isEqualTo(5);
    }

    @Test
    void shouldReportNoChangeWhenThereIsNothingToCompareAgainst() {
        givenOverview(Map.of(UserFeature.PROGRAMMING_ONLINE_EDITOR, used(10, 0, 0)));

        assertThat(areaOf(service.buildWeeklyDigest(), ProductArea.PROGRAMMING).changePercent()).isNull();
    }

    @Test
    void shouldReportADrop() {
        givenOverview(Map.of(UserFeature.PROGRAMMING_ONLINE_EDITOR, used(25, 0, 0)));
        when(repository.findFeatureCallsBetween(any(), any()))
                .thenReturn(List.of(new FeatureUsageLabelCallsDTO(UserFeature.PROGRAMMING_ONLINE_EDITOR.name(), FeatureInteraction.ACTION, 100)));

        assertThat(areaOf(service.buildWeeklyDigest(), ProductArea.PROGRAMMING).changePercent()).isEqualTo(-75);
    }

    @Test
    void shouldBeEmptyWhenNothingWasUsed() {
        givenOverview(Map.of(UserFeature.HYPERION_VARIANT_GENERATION, onlyAutomatic(50), UserFeature.DEIMOS, unused()));

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        // automatic calls alone must not make the digest look like a week of use
        assertThat(digest.isEmpty()).isTrue();
        assertThat(digest.activeAreas()).isEmpty();
    }

    @Test
    void shouldCoverExactlyOneWeek() {
        givenOverview(Map.of());

        FeatureUsageDigestDTO digest = service.buildWeeklyDigest();

        verify(queryService).getOverview(eq(FeatureUsageDigestService.DIGEST_WINDOW_IN_DAYS), eq(null));
        assertThat(digest.days()).isEqualTo(7);
        assertThat(digest.from()).isEqualTo(FROM);
        assertThat(digest.to()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    private static FeatureUsageAreaSummaryDTO areaOf(FeatureUsageDigestDTO digest, ProductArea area) {
        return digest.activeAreas().stream().filter(summary -> summary.area() == area).findFirst().orElseThrow();
    }

    /**
     * Stubs the report the digest is built from. Every catalogue feature that is not listed is reported as not available,
     * which is what an overview of a deployment with only these features switched on looks like.
     */
    private void givenOverview(Map<UserFeature, Usage> usage) {
        Map<UserFeature, UserFeatureUsageDTO> features = Arrays.stream(UserFeature.values())
                .collect(Collectors.toMap(Function.identity(), feature -> usage.getOrDefault(feature, Usage.NOT_AVAILABLE).toDTO(feature)));
        List<UserFeatureUsageDTO> list = Arrays.stream(UserFeature.values()).map(features::get).toList();
        long available = list.stream().filter(feature -> feature.status() != FeatureUsageStatus.NOT_AVAILABLE).count();
        long used = list.stream().filter(feature -> feature.status() == FeatureUsageStatus.USED).count();
        long onlyAutomatic = list.stream().filter(feature -> feature.status() == FeatureUsageStatus.ONLY_AUTOMATIC).count();
        long unused = list.stream().filter(feature -> feature.status() == FeatureUsageStatus.UNUSED).count();
        long actions = list.stream().mapToLong(UserFeatureUsageDTO::actionCount).sum();
        long views = list.stream().mapToLong(UserFeatureUsageDTO::viewCount).sum();
        when(queryService.getOverview(anyInt(), any())).thenReturn(new FeatureUsageOverviewDTO(7, FROM, null, available, used, onlyAutomatic, unused,
                UserFeature.values().length - available, 0, 3, actions, views, 0, 0, Instant.now(), Instant.now().minusSeconds(3600), list, List.of(), List.of()));
    }

    private static Usage used(long actions, long views, long errors) {
        return new Usage(FeatureUsageStatus.USED, actions, views, 0, errors);
    }

    private static Usage unused() {
        return new Usage(FeatureUsageStatus.UNUSED, 0, 0, 0, 0);
    }

    private static Usage onlyAutomatic(long automatic) {
        return new Usage(FeatureUsageStatus.ONLY_AUTOMATIC, 0, 0, automatic, 0);
    }

    private record Usage(FeatureUsageStatus status, long actions, long views, long automatic, long errors) {

        static final Usage NOT_AVAILABLE = new Usage(FeatureUsageStatus.NOT_AVAILABLE, 0, 0, 0, 0);

        UserFeatureUsageDTO toDTO(UserFeature feature) {
            return new UserFeatureUsageDTO(feature, feature.getArea(), status, false, actions, views, automatic, 0, errors, 0, 0, 0, 0, null, null, List.of(), 1, true);
        }
    }
}
