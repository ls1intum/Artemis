package de.tum.cit.aet.artemis.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.admin.dto.FeatureAdoptionDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageActiveDaysDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.FeatureUsageDaily;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;
import de.tum.cit.aet.artemis.core.repository.TrackedFeatureRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the admin read API against a real database.
 * <p>
 * The aggregate query is the reason this test exists rather than a unit test: it drives the inventory with an outer join
 * so that features with no usage still come back, and that is exactly the kind of thing that either works against the
 * database or silently returns the wrong set of rows.
 * <p>
 * Named {@code *Test} rather than {@code *IntegrationTest} on purpose. The admin module forces {@code *IntegrationTest}
 * classes onto a base class with a different resource lock, and this test writes to shared tables.
 */
class AdminFeatureUsageResourceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "featureusage";

    @Autowired
    private TrackedFeatureRepository trackedFeatureRepository;

    @Autowired
    private FeatureUsageDailyRepository featureUsageDailyRepository;

    private TrackedFeature usedFeature;

    private TrackedFeature unusedFeature;

    private TrackedFeature retiredFeature;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Instant registeredNow = Instant.now();
        usedFeature = trackedFeatureRepository
                .save(new TrackedFeature(FeatureKind.REST, "programming", "GET api/programming/used-in-test", "configuration/used-in-test", registeredNow));
        unusedFeature = trackedFeatureRepository.save(new TrackedFeature(FeatureKind.REST, "programming", "GET api/programming/unused-in-test", null, registeredNow));
        // no node has reported this endpoint for a month, so this version no longer has it
        retiredFeature = trackedFeatureRepository
                .save(new TrackedFeature(FeatureKind.REST, "programming", "GET api/programming/retired-in-test", null, registeredNow.minus(30, ChronoUnit.DAYS)));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        featureUsageDailyRepository.save(new FeatureUsageDaily(usedFeature.getId(), today, Role.STUDENT, 10, 2, 500, 120));
        featureUsageDailyRepository.save(new FeatureUsageDaily(usedFeature.getId(), today.minusDays(2), Role.INSTRUCTOR, 5, 0, 100, 40));
        // outside the 7 day window, so it must not be counted there but must be counted in the 30 day window
        featureUsageDailyRepository.save(new FeatureUsageDaily(usedFeature.getId(), today.minusDays(20), Role.STUDENT, 1000, 0, 1000, 900));
    }

    @AfterEach
    void tearDown() {
        // the inventory is process wide, so leftovers would show up in every later assertion on these tables
        featureUsageDailyRepository.deleteAll(featureUsageDailyRepository.findAll().stream().filter(bucket -> bucket.getFeatureId().equals(usedFeature.getId())).toList());
        trackedFeatureRepository.deleteAll(List.of(usedFeature, unusedFeature, retiredFeature));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectANonAdmin() throws Exception {
        request.get("/api/admin/feature-usage", HttpStatus.FORBIDDEN, FeatureUsageOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldAggregateOnlyTheSelectedWindow() throws Exception {
        var overview = getOverview(7);

        assertThat(overview.days()).isEqualTo(7);
        assertThat(overview.from()).isEqualTo(LocalDate.now(ZoneOffset.UTC).minusDays(6));
        // 10 + 5 from within the window, the 1000 from 20 days ago excluded
        assertThat(entryFor(overview, usedFeature).callCount()).isEqualTo(15);
        assertThat(entryFor(overview, usedFeature).errorCount()).isEqualTo(2);
        assertThat(entryFor(overview, usedFeature).durationSumMs()).isEqualTo(600);
        assertThat(entryFor(overview, usedFeature).durationMaxMs()).isEqualTo(120);
        // two distinct days inside the window
        assertThat(entryFor(overview, usedFeature).activeDays()).isEqualTo(2);
        assertThat(entryFor(overview, usedFeature).lastUsedDay()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldIncludeTheOlderBucketInALongerWindow() throws Exception {
        assertThat(entryFor(getOverview(30), usedFeature).callCount()).isEqualTo(1015);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportAFeatureThatSawNoUsage() throws Exception {
        var overview = getOverview(7);

        // the whole point of the page: an unused feature must be listed, not filtered out
        var entry = entryFor(overview, unusedFeature);
        assertThat(entry.callCount()).isZero();
        assertThat(entry.activeDays()).isZero();
        assertThat(entry.lastUsedDay()).isNull();
        assertThat(entry.retired()).isFalse();
        assertThat(overview.unusedFeatures()).isPositive();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldSeparateARetiredFeatureFromAnUnusedOne() throws Exception {
        var overview = getOverview(7);

        // Both have zero calls, but only one still exists. Without the distinction the actionable list slowly fills up with
        // endpoints that were deleted releases ago, and stops being worth reading.
        assertThat(entryFor(overview, retiredFeature).retired()).isTrue();
        assertThat(entryFor(overview, unusedFeature).retired()).isFalse();
        assertThat(overview.retiredFeatures()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotCountRetiredFeaturesAsUnused() throws Exception {
        var overview = getOverview(7);

        long unusedAndStillOffered = overview.features().stream().filter(entry -> entry.callCount() == 0 && !entry.retired()).count();
        assertThat(overview.unusedFeatures()).isEqualTo(unusedAndStillOffered);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRestrictTheCountersToTheRequestedRole() throws Exception {
        var studentOnly = getOverview(7, Role.STUDENT);

        // only the 10 student calls of the last 7 days, not the 5 instructor ones
        assertThat(studentOnly.callerRole()).isEqualTo(Role.STUDENT);
        assertThat(entryFor(studentOnly, usedFeature).callCount()).isEqualTo(10);
        assertThat(entryFor(studentOnly, usedFeature).activeDays()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldStillListEveryFeatureWhenFilteringByARoleThatNeverCalledIt() throws Exception {
        var editorOnly = getOverview(7, Role.EDITOR);

        // the role predicate has to sit in the join, otherwise the features this role never touched vanish and the report
        // answers the opposite of the question that was asked
        assertThat(editorOnly.features()).hasSameSizeAs(getOverview(7).features());
        assertThat(entryFor(editorOnly, usedFeature).callCount()).isZero();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldOnlyEverAdvanceTheRegistrationTimestamp() {
        Instant original = trackedFeatureRepository.findById(usedFeature.getId()).orElseThrow().getLastRegisteredAt();

        // a node that started earlier than the last one must not drag live features back towards looking retired
        trackedFeatureRepository.markStillRegistered(List.of(usedFeature.getId()), original.minus(10, ChronoUnit.DAYS));
        assertThat(trackedFeatureRepository.findById(usedFeature.getId()).orElseThrow().getLastRegisteredAt()).isEqualTo(original);

        Instant later = original.plus(1, ChronoUnit.DAYS);
        trackedFeatureRepository.markStillRegistered(List.of(usedFeature.getId()), later);
        assertThat(trackedFeatureRepository.findById(usedFeature.getId()).orElseThrow().getLastRegisteredAt()).isEqualTo(later);
    }

    /**
     * The retirement reference is the newest REST registration, and it has to stay that way.
     * <p>
     * Git and background features are registered the first time they are used, not at startup, so their
     * {@code lastRegisteredAt} is a first-use time that can be arbitrarily later than the REST inventory refresh.
     * Taking the maximum across every kind let one such feature become the reference and push it past every REST
     * endpoint registered at startup, marking all of them retired and emptying the unused list this page exists to
     * produce. Asserting on the retired flags alone would not catch a regression here, because the flags are only wrong
     * relative to that reference, so this also pins the reported timestamp.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotLetALateFirstUsedGitFeatureRetireEveryRestEndpoint() throws Exception {
        Instant restRegisteredAt = trackedFeatureRepository.findById(usedFeature.getId()).orElseThrow().getLastRegisteredAt();
        // A git feature used for the first time long after startup, which is ordinary rather than exceptional
        TrackedFeature lateGitFeature = trackedFeatureRepository
                .save(new TrackedFeature(FeatureKind.GIT, "programming", "git clone late-in-test", null, restRegisteredAt.plus(30, ChronoUnit.DAYS)));
        try {
            var overview = getOverview(7);

            assertThat(entryFor(overview, usedFeature).retired()).isFalse();
            assertThat(entryFor(overview, unusedFeature).retired()).isFalse();
            // The one genuinely stale endpoint is still recognised, so the scoping did not simply disable retirement
            assertThat(entryFor(overview, retiredFeature).retired()).isTrue();
            assertThat(overview.unusedFeatures()).isPositive();
            // The reported refresh time is the REST registration, not the git feature's first use
            assertThat(overview.inventoryRefreshedAt()).isBefore(lateGitFeature.getLastRegisteredAt());
        }
        finally {
            trackedFeatureRepository.delete(lateGitFeature);
        }
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCarryTheFeatureLabelAndModule() throws Exception {
        var entry = entryFor(getOverview(7), usedFeature);

        assertThat(entry.module()).isEqualTo("programming");
        assertThat(entry.featureLabel()).isEqualTo("configuration/used-in-test");
        assertThat(entry.featureKind()).isEqualTo(FeatureKind.REST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportSinceWhenItHasBeenRecording() throws Exception {
        var overview = getOverview(7);

        // the report must not imply more evidence than it has: an instance recording for a week cannot support a
        // "unused over 180 days" conclusion, and this is the only field that reveals the difference
        assertThat(overview.recordingSince()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportTheRoleDistribution() throws Exception {
        var overview = getOverview(7);

        assertThat(overview.roleDistribution()).isNotNull();
        assertThat(overview.roleDistribution()).anySatisfy(share -> {
            assertThat(share.callerRole()).isEqualTo(Role.STUDENT);
            assertThat(share.callCount()).isEqualTo(10);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectAWindowThatIsNotOffered() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("days", "5");

        request.get("/api/admin/feature-usage", HttpStatus.BAD_REQUEST, FeatureUsageOverviewDTO.class, params);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnTheDailyTrendOfAFeature() throws Exception {
        List<FeatureUsageTrendPointDTO> trend = request.get("/api/admin/feature-usage/trend?featureIds=" + usedFeature.getId() + "&days=7", HttpStatus.OK, new TypeReference<>() {
        });

        assertThat(trend).hasSize(2).isSortedAccordingTo((first, second) -> first.usageDay().compareTo(second.usageDay()));
        assertThat(trend.getLast().callCount()).isEqualTo(10);
    }

    /**
     * A labelled feature is served by several endpoints, so the chart has to sum them. Charting one of them would report a
     * fraction of the feature's usage as the feature's usage.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldSumTheTrendAcrossEveryEndpointOfAFeature() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // the bucket is removed with its feature in the cleanup, the foreign key cascades
        featureUsageDailyRepository.save(new FeatureUsageDaily(retiredFeature.getId(), today, Role.STUDENT, 7, 0, 70, 20));

        List<FeatureUsageTrendPointDTO> trend = request.get(
                "/api/admin/feature-usage/trend?featureIds=" + usedFeature.getId() + "&featureIds=" + retiredFeature.getId() + "&days=7", HttpStatus.OK, new TypeReference<>() {
                });

        assertThat(trend).isNotEmpty();
        // 10 from one endpoint and 7 from the other, on the same day
        assertThat(trend.getLast().usageDay()).isEqualTo(today);
        assertThat(trend.getLast().callCount()).isEqualTo(17);
    }

    /**
     * The overview can be narrowed to a caller role, and a chart opened on such a filtered row has to answer the same
     * question. Summing every role in the trend would silently widen the numbers the moment the chart is opened.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRestrictTheTrendToTheRequestedRole() throws Exception {
        // the fixture holds a STUDENT bucket today and an INSTRUCTOR bucket two days ago, both on this feature
        List<FeatureUsageTrendPointDTO> studentOnly = request.get("/api/admin/feature-usage/trend?featureIds=" + usedFeature.getId() + "&days=7&callerRole=STUDENT", HttpStatus.OK,
                new TypeReference<>() {
                });

        assertThat(studentOnly).hasSize(1);
        assertThat(studentOnly.getFirst().usageDay()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(studentOnly.getFirst().callCount()).isEqualTo(10);

        List<FeatureUsageTrendPointDTO> instructorOnly = request.get("/api/admin/feature-usage/trend?featureIds=" + usedFeature.getId() + "&days=7&callerRole=INSTRUCTOR",
                HttpStatus.OK, new TypeReference<>() {
                });

        assertThat(instructorOnly).hasSize(1);
        assertThat(instructorOnly.getFirst().callCount()).isEqualTo(5);
    }

    /**
     * The table groups the endpoints behind one label into a single row, and its active-day count cannot be derived from the
     * per-endpoint counts: summing double counts a shared day, and taking the largest misses the days only one endpoint was
     * used on. The grouped count therefore comes from the database.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportTheDistinctActiveDaysPerGroupedFeature() throws Exception {
        FeatureUsageOverviewDTO overview = getOverview(7, null);

        assertThat(overview.activeDaysPerFeature()).isNotEmpty();
        var usedFeatureDays = overview.activeDaysPerFeature().stream().filter(entry -> "configuration/used-in-test".equals(entry.featureKey())).findFirst();
        // two buckets on two different days within the window, so the union is two days
        assertThat(usedFeatureDays).isPresent().get().extracting(FeatureUsageActiveDaysDTO::activeDays).isEqualTo(2L);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnAdoptionCountsFromEveryContributingModule() throws Exception {
        List<FeatureAdoptionDTO> adoption = request.get("/api/admin/feature-usage/adoption", HttpStatus.OK, new TypeReference<>() {
        });

        assertThat(adoption).isNotEmpty();
        assertThat(adoption).extracting(FeatureAdoptionDTO::module).contains("programming", "course", "quiz");
        assertThat(adoption).extracting(FeatureAdoptionDTO::key).contains("static-code-analysis", "communication");
        assertThat(adoption).allSatisfy(entry -> assertThat(entry.count()).isLessThanOrEqualTo(entry.total()));
    }

    private FeatureUsageOverviewDTO getOverview(int days) throws Exception {
        return getOverview(days, null);
    }

    private FeatureUsageOverviewDTO getOverview(int days, @Nullable Role callerRole) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("days", String.valueOf(days));
        if (callerRole != null) {
            params.add("callerRole", callerRole.name());
        }
        return request.get("/api/admin/feature-usage", HttpStatus.OK, FeatureUsageOverviewDTO.class, params);
    }

    private static FeatureUsageEntryDTO entryFor(FeatureUsageOverviewDTO overview, TrackedFeature feature) {
        assertThat(overview.features()).isNotNull();
        return overview.features().stream().filter(entry -> entry.featureId() == feature.getId()).findFirst().orElseThrow();
    }
}
