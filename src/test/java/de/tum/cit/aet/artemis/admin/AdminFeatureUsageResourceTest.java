package de.tum.cit.aet.artemis.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.admin.dto.FeatureAdoptionDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageEntryDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageOverviewDTO;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageStatus;
import de.tum.cit.aet.artemis.admin.dto.FeatureUsageTrendPointDTO;
import de.tum.cit.aet.artemis.admin.dto.UserFeatureUsageDTO;
import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.FeatureUsageDaily;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.FeatureUsageDailyRepository;
import de.tum.cit.aet.artemis.core.repository.TrackedFeatureRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.featureusage.ProductArea;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Tests the admin read API against a real database.
 * <p>
 * The aggregate queries are the reason this test exists rather than a unit test: they drive the inventory with an outer
 * join so that features with no usage still come back, and they count distinct days per feature across endpoints. Both
 * either work against the database or silently return the wrong numbers.
 * <p>
 * The fixture models the cases the page exists to tell apart: a feature that is used, one whose only traffic is an
 * automatic status probe, one that is read but never acted on, one that nobody touched, and one this deployment does not
 * offer. Tracking is switched off in tests, so the inventory holds nothing but these rows.
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

    private final List<TrackedFeature> fixture = new ArrayList<>();

    /** The online code editor, used by students and instructors: an action in one module, a git view in another. */
    private TrackedFeature editorCommit;

    private TrackedFeature editorClone;

    /** AI exercise variants: a status probe on every page load and a generation nobody ever started. */
    private TrackedFeature variantJobsProbe;

    private TrackedFeature variantGeneration;

    /** FAQs: read, but nobody created one. */
    private TrackedFeature faqList;

    private TrackedFeature faqCreation;

    /** Deimos: offered, never called. */
    private TrackedFeature deimosRun;

    /** An endpoint no node has reported for a month, carrying a label from before the catalogue. */
    private TrackedFeature retiredEndpoint;

    /** Research data collection: its only endpoint is gone, so this deployment does not offer it. */
    private TrackedFeature retiredScienceEndpoint;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 1);
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        editorCommit = save(FeatureKind.REST, "programming", "POST api/programming/commit-in-test", UserFeature.PROGRAMMING_ONLINE_EDITOR, FeatureInteraction.ACTION, now);
        editorClone = save(FeatureKind.GIT, "localvc", "fetch/assignment-in-test", UserFeature.PROGRAMMING_ONLINE_EDITOR, FeatureInteraction.VIEW, now);
        variantJobsProbe = save(FeatureKind.REST, "hyperion", "GET api/hyperion/variant-jobs-in-test", UserFeature.HYPERION_VARIANT_GENERATION, FeatureInteraction.AUTOMATIC, now);
        variantGeneration = save(FeatureKind.REST, "hyperion", "POST api/hyperion/generate-variant-in-test", UserFeature.HYPERION_VARIANT_GENERATION, FeatureInteraction.ACTION,
                now);
        faqList = save(FeatureKind.REST, "communication", "GET api/communication/faqs-in-test", UserFeature.FAQ, FeatureInteraction.VIEW, now);
        faqCreation = save(FeatureKind.REST, "communication", "POST api/communication/faqs-in-test", UserFeature.FAQ, FeatureInteraction.ACTION, now);
        deimosRun = save(FeatureKind.REST, "deimos", "POST api/deimos/analysis-runs-in-test", UserFeature.DEIMOS, FeatureInteraction.ACTION, now);
        retiredEndpoint = trackedFeatureRepository.save(new TrackedFeature(FeatureKind.REST, "programming", "GET api/programming/retired-in-test", "configuration/old-label",
                FeatureInteraction.VIEW, "OldResource", monthAgo));
        fixture.add(retiredEndpoint);
        retiredScienceEndpoint = save(FeatureKind.REST, "atlas", "PUT api/atlas/science-in-test", UserFeature.SCIENCE, FeatureInteraction.ACTION, monthAgo);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        bucket(editorCommit, today, Role.STUDENT, 10, 2, 500, 120);
        bucket(editorCommit, today.minusDays(2), Role.INSTRUCTOR, 5, 0, 100, 40);
        // outside the 7 day window, so it must not be counted there but must be counted in the 30 day window
        bucket(editorCommit, today.minusDays(20), Role.STUDENT, 1000, 0, 1000, 900);
        // a clone on a day without a commit, so the active days of the feature are more than those of either endpoint
        bucket(editorClone, today.minusDays(4), Role.ANONYMOUS, 3, 0, 30, 10);
        bucket(variantJobsProbe, today, Role.EDITOR, 400, 0, 400, 5);
        bucket(variantJobsProbe, today.minusDays(1), Role.EDITOR, 300, 1, 300, 5);
        bucket(faqList, today, Role.STUDENT, 20, 0, 200, 15);
    }

    private TrackedFeature save(FeatureKind kind, String module, String identifier, UserFeature feature, FeatureInteraction interaction, Instant registeredAt) {
        String resource = kind == FeatureKind.REST ? "InTestResource" : null;
        TrackedFeature saved = trackedFeatureRepository.save(new TrackedFeature(kind, module, identifier, feature.name(), interaction, resource, registeredAt));
        fixture.add(saved);
        return saved;
    }

    private void bucket(TrackedFeature feature, LocalDate day, Role role, long calls, long errors, long durationSumMs, int durationMaxMs) {
        featureUsageDailyRepository.save(new FeatureUsageDaily(feature.getId(), day, role, calls, errors, durationSumMs, durationMaxMs));
    }

    @AfterEach
    void tearDown() {
        // the inventory is process wide, so leftovers would show up in every later assertion on these tables; the buckets
        // are removed with their features, the foreign key cascades
        trackedFeatureRepository.deleteAll(fixture);
        fixture.clear();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void shouldRejectANonAdmin() throws Exception {
        request.get("/api/admin/feature-usage", HttpStatus.FORBIDDEN, FeatureUsageOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldSumAFeatureAcrossItsEndpointsAndModules() throws Exception {
        var overview = getOverview(7);
        var editor = featureOf(overview, UserFeature.PROGRAMMING_ONLINE_EDITOR);

        assertThat(overview.days()).isEqualTo(7);
        assertThat(overview.from()).isEqualTo(LocalDate.now(ZoneOffset.UTC).minusDays(6));
        assertThat(editor.status()).isEqualTo(FeatureUsageStatus.USED);
        assertThat(editor.area()).isEqualTo(ProductArea.PROGRAMMING);
        // 10 + 5 commits from within the window, the 1000 from 20 days ago excluded, and 3 clones
        assertThat(editor.actionCount()).isEqualTo(15);
        assertThat(editor.viewCount()).isEqualTo(3);
        assertThat(editor.errorCount()).isEqualTo(2);
        assertThat(editor.durationSumMs()).isEqualTo(630);
        assertThat(editor.durationMaxMs()).isEqualTo(120);
        assertThat(editor.modules()).containsExactly("localvc", "programming");
        assertThat(editor.endpointCount()).isEqualTo(2);
        assertThat(editor.hasActionEndpoints()).isTrue();
        assertThat(editor.noActions()).isFalse();
        assertThat(editor.lastUsedDay()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(editor.lastActionDay()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
    }

    /**
     * Active days cannot be derived from the per-endpoint counts: summing double counts a shared day, and taking the
     * largest misses the days only one endpoint was used on. The feature count therefore comes from the database.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCountTheDistinctDaysAcrossEveryEndpointOfAFeature() throws Exception {
        var editor = featureOf(getOverview(7), UserFeature.PROGRAMMING_ONLINE_EDITOR);

        // commits on two days, a clone on a third
        assertThat(editor.activeDays()).isEqualTo(3);
        assertThat(editor.actionDays()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldIncludeTheOlderBucketInALongerWindow() throws Exception {
        assertThat(featureOf(getOverview(30), UserFeature.PROGRAMMING_ONLINE_EDITOR).actionCount()).isEqualTo(1015);
    }

    /**
     * The case this page was redesigned for: a status probe that the client sends on every page load made a feature nobody
     * used look like the busiest one on the page.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldNotCountAutomaticCallsAsUse() throws Exception {
        var overview = getOverview(7);
        var variants = featureOf(overview, UserFeature.HYPERION_VARIANT_GENERATION);

        assertThat(variants.status()).isEqualTo(FeatureUsageStatus.ONLY_AUTOMATIC);
        assertThat(variants.automaticCount()).isEqualTo(700);
        assertThat(variants.actionCount()).isZero();
        assertThat(variants.viewCount()).isZero();
        // neither the probe's days nor its error count as use of the feature
        assertThat(variants.activeDays()).isZero();
        assertThat(variants.errorCount()).isZero();
        assertThat(variants.lastUsedDay()).isNull();
        assertThat(overview.onlyAutomatic()).isEqualTo(1);
        assertThat(overview.automaticCount()).isEqualTo(700);
        // the probes are not attributed to anybody in the role distribution either
        assertThat(overview.roleDistribution()).noneSatisfy(share -> assertThat(share.callerRole()).isEqualTo(Role.EDITOR));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldFlagAFeatureThatIsOnlyViewed() throws Exception {
        var overview = getOverview(7);
        var faq = featureOf(overview, UserFeature.FAQ);

        // reading FAQs is use, so the status is used, but nobody created one, which the flag reports
        assertThat(faq.status()).isEqualTo(FeatureUsageStatus.USED);
        assertThat(faq.noActions()).isTrue();
        assertThat(faq.viewCount()).isEqualTo(20);
        assertThat(faq.lastActionDay()).isNull();
        assertThat(overview.noActions()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportAFeatureThatSawNoUsage() throws Exception {
        var overview = getOverview(7);
        var deimos = featureOf(overview, UserFeature.DEIMOS);

        // the whole point of the page: an unused feature must be listed, not filtered out
        assertThat(deimos.status()).isEqualTo(FeatureUsageStatus.UNUSED);
        assertThat(deimos.actionCount()).isZero();
        assertThat(deimos.activeDays()).isZero();
        assertThat(overview.unusedFeatures()).isEqualTo(1);
        assertThat(overview.usedFeatures()).isEqualTo(2);
        assertThat(overview.availableFeatures()).isEqualTo(4);
    }

    /**
     * A feature whose endpoints are all gone, or that never had one here, is not offered by this deployment. Its zero
     * usage is not a decision to make, so it must not end up among the unused features.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReportEveryCatalogueFeatureAndSeparateTheUnavailableOnes() throws Exception {
        var overview = getOverview(7);

        assertThat(overview.features()).extracting(UserFeatureUsageDTO::feature).containsExactly(UserFeature.values());
        assertThat(featureOf(overview, UserFeature.SCIENCE).status()).isEqualTo(FeatureUsageStatus.NOT_AVAILABLE);
        assertThat(featureOf(overview, UserFeature.CALENDAR).status()).isEqualTo(FeatureUsageStatus.NOT_AVAILABLE);
        assertThat(overview.notAvailable()).isEqualTo(UserFeature.values().length - overview.availableFeatures());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldListEveryEndpointWithHowItsCallsCount() throws Exception {
        var overview = getOverview(7);

        var probe = endpointOf(overview, variantJobsProbe);
        assertThat(probe.interaction()).isEqualTo(FeatureInteraction.AUTOMATIC);
        assertThat(probe.resource()).isEqualTo("InTestResource");
        assertThat(probe.featureLabel()).isEqualTo(UserFeature.HYPERION_VARIANT_GENERATION.name());
        assertThat(probe.callCount()).isEqualTo(700);
        assertThat(endpointOf(overview, editorClone).featureKind()).isEqualTo(FeatureKind.GIT);
        assertThat(endpointOf(overview, editorClone).resource()).isNull();
        assertThat(endpointOf(overview, deimosRun).callCount()).isZero();
        assertThat(endpointOf(overview, variantGeneration).callCount()).isZero();
        assertThat(endpointOf(overview, faqCreation).interaction()).isEqualTo(FeatureInteraction.ACTION);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldSeparateARetiredEndpointFromAnUnusedOne() throws Exception {
        var overview = getOverview(7);

        // Both have zero calls, but only one still exists. Without the distinction the actionable list slowly fills up with
        // endpoints that were deleted releases ago, and stops being worth reading.
        assertThat(endpointOf(overview, retiredEndpoint).retired()).isTrue();
        assertThat(endpointOf(overview, retiredScienceEndpoint).retired()).isTrue();
        assertThat(endpointOf(overview, deimosRun).retired()).isFalse();
        assertThat(overview.retiredEndpoints()).isEqualTo(2);
        // a label from before the catalogue belongs to no feature, but the endpoint keeps its history
        assertThat(endpointOf(overview, retiredEndpoint).featureLabel()).isEqualTo("configuration/old-label");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRestrictTheCountersToTheRequestedRole() throws Exception {
        var studentOnly = getOverview(7, Role.STUDENT);
        var editor = featureOf(studentOnly, UserFeature.PROGRAMMING_ONLINE_EDITOR);

        // only the 10 student commits of the last 7 days, not the 5 instructor ones, and no clones, which are anonymous
        assertThat(studentOnly.callerRole()).isEqualTo(Role.STUDENT);
        assertThat(editor.actionCount()).isEqualTo(10);
        assertThat(editor.viewCount()).isZero();
        assertThat(editor.activeDays()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldStillListEveryFeatureWhenFilteringByARoleThatNeverCalledIt() throws Exception {
        var tutorOnly = getOverview(7, Role.TEACHING_ASSISTANT);

        // the role predicate has to sit in the join, otherwise the features this role never touched vanish and the report
        // answers the opposite of the question that was asked
        assertThat(tutorOnly.endpoints()).hasSameSizeAs(getOverview(7).endpoints());
        assertThat(tutorOnly.features()).hasSize(UserFeature.values().length);
        assertThat(featureOf(tutorOnly, UserFeature.PROGRAMMING_ONLINE_EDITOR).status()).isEqualTo(FeatureUsageStatus.UNUSED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldOnlyEverAdvanceTheRegistrationTimestamp() {
        Instant original = trackedFeatureRepository.findById(editorCommit.getId()).orElseThrow().getLastRegisteredAt();

        // a node that started earlier than the last one must not drag live features back towards looking retired
        trackedFeatureRepository.markStillRegistered(List.of(editorCommit.getId()), original.minus(10, ChronoUnit.DAYS));
        assertThat(trackedFeatureRepository.findById(editorCommit.getId()).orElseThrow().getLastRegisteredAt()).isEqualTo(original);

        Instant later = original.plus(1, ChronoUnit.DAYS);
        trackedFeatureRepository.markStillRegistered(List.of(editorCommit.getId()), later);
        assertThat(trackedFeatureRepository.findById(editorCommit.getId()).orElseThrow().getLastRegisteredAt()).isEqualTo(later);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReclassifyAnEndpoint() {
        trackedFeatureRepository.updateClassification(faqList.getId(), UserFeature.MESSAGING.name(), FeatureInteraction.AUTOMATIC, "OtherResource");

        TrackedFeature reclassified = trackedFeatureRepository.findById(faqList.getId()).orElseThrow();
        assertThat(reclassified.getFeatureLabel()).isEqualTo(UserFeature.MESSAGING.name());
        assertThat(reclassified.getInteraction()).isEqualTo(FeatureInteraction.AUTOMATIC);
        assertThat(reclassified.getResource()).isEqualTo("OtherResource");
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
        Instant restRegisteredAt = trackedFeatureRepository.findById(editorCommit.getId()).orElseThrow().getLastRegisteredAt();
        // A git feature used for the first time long after startup, which is ordinary rather than exceptional
        TrackedFeature lateGitFeature = save(FeatureKind.GIT, "localvc", "push/late-in-test", UserFeature.PROGRAMMING_LOCAL_IDE, FeatureInteraction.ACTION,
                restRegisteredAt.plus(30, ChronoUnit.DAYS));

        var overview = getOverview(7);

        assertThat(endpointOf(overview, editorCommit).retired()).isFalse();
        assertThat(endpointOf(overview, deimosRun).retired()).isFalse();
        // The one genuinely stale endpoint is still recognised, so the scoping did not simply disable retirement
        assertThat(endpointOf(overview, retiredEndpoint).retired()).isTrue();
        assertThat(featureOf(overview, UserFeature.DEIMOS).status()).isEqualTo(FeatureUsageStatus.UNUSED);
        // The reported refresh time is the REST registration, not the git feature's first use
        assertThat(overview.inventoryRefreshedAt()).isBefore(lateGitFeature.getLastRegisteredAt());
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
    void shouldReportTheRoleDistributionOfActionsAndViews() throws Exception {
        var overview = getOverview(7);

        // 10 commits and 20 FAQ views by students
        assertThat(overview.roleDistribution()).anySatisfy(share -> {
            assertThat(share.callerRole()).isEqualTo(Role.STUDENT);
            assertThat(share.callCount()).isEqualTo(30);
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectAWindowThatIsNotOffered() throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("days", "5");

        request.get("/api/admin/feature-usage", HttpStatus.BAD_REQUEST, FeatureUsageOverviewDTO.class, params);
    }

    /**
     * A feature is served by several endpoints, so its chart has to sum them, and it has to keep the interactions apart:
     * a chart that added the probes to the generations would show the very picture this page was redesigned to avoid.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnTheDailyTrendOfAFeaturePerInteraction() throws Exception {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        bucket(variantGeneration, today, Role.EDITOR, 2, 0, 2000, 1500);

        List<FeatureUsageTrendPointDTO> trend = getTrend("feature=" + UserFeature.HYPERION_VARIANT_GENERATION.name() + "&days=7");

        assertThat(trend).isSortedAccordingTo((first, second) -> first.usageDay().compareTo(second.usageDay()));
        assertThat(trend).filteredOn(point -> point.usageDay().equals(today)).extracting(FeatureUsageTrendPointDTO::interaction, FeatureUsageTrendPointDTO::callCount)
                .containsExactlyInAnyOrder(tuple(FeatureInteraction.AUTOMATIC, 400L), tuple(FeatureInteraction.ACTION, 2L));
        assertThat(trend).filteredOn(point -> point.usageDay().equals(today.minusDays(1))).extracting(FeatureUsageTrendPointDTO::callCount).containsExactly(300L);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldSumTheTrendOfAFeatureAcrossModules() throws Exception {
        List<FeatureUsageTrendPointDTO> trend = getTrend("feature=" + UserFeature.PROGRAMMING_ONLINE_EDITOR.name() + "&days=7");

        // commits today and two days ago, a clone four days ago
        assertThat(trend).hasSize(3);
        assertThat(trend.getFirst().interaction()).isEqualTo(FeatureInteraction.VIEW);
        assertThat(trend.getLast().callCount()).isEqualTo(10);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnTheDailyTrendOfIndividualEndpoints() throws Exception {
        List<FeatureUsageTrendPointDTO> trend = getTrend("featureIds=" + editorCommit.getId() + "&featureIds=" + faqList.getId() + "&days=7");

        // 10 commits and 20 FAQ views today, and 5 commits two days ago
        assertThat(trend).filteredOn(point -> point.usageDay().equals(LocalDate.now(ZoneOffset.UTC))).extracting(FeatureUsageTrendPointDTO::callCount)
                .containsExactlyInAnyOrder(10L, 20L);
        assertThat(trend).hasSize(3);
    }

    /**
     * The overview can be narrowed to a caller role, and a chart opened on such a filtered row has to answer the same
     * question. Summing every role in the trend would silently widen the numbers the moment the chart is opened.
     */
    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRestrictTheTrendToTheRequestedRole() throws Exception {
        List<FeatureUsageTrendPointDTO> studentOnly = getTrend("feature=" + UserFeature.PROGRAMMING_ONLINE_EDITOR.name() + "&days=7&callerRole=STUDENT");
        assertThat(studentOnly).hasSize(1);
        assertThat(studentOnly.getFirst().usageDay()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        assertThat(studentOnly.getFirst().callCount()).isEqualTo(10);

        List<FeatureUsageTrendPointDTO> instructorOnly = getTrend("featureIds=" + editorCommit.getId() + "&days=7&callerRole=INSTRUCTOR");
        assertThat(instructorOnly).hasSize(1);
        assertThat(instructorOnly.getFirst().callCount()).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldRejectATrendRequestWithoutExactlyOneSelector() throws Exception {
        request.get("/api/admin/feature-usage/trend?days=7", HttpStatus.BAD_REQUEST, List.class);
        request.get("/api/admin/feature-usage/trend?feature=FAQ&featureIds=" + faqList.getId() + "&days=7", HttpStatus.BAD_REQUEST, List.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturnAdoptionCountsWithTheFeatureTheySwitchOn() throws Exception {
        List<FeatureAdoptionDTO> adoption = request.get("/api/admin/feature-usage/adoption", HttpStatus.OK, new TypeReference<>() {
        });

        assertThat(adoption).isNotEmpty();
        assertThat(adoption).extracting(FeatureAdoptionDTO::module).contains("programming", "course", "quiz");
        assertThat(adoption).filteredOn(entry -> "static-code-analysis".equals(entry.key())).extracting(FeatureAdoptionDTO::feature)
                .containsExactly(UserFeature.PROGRAMMING_GRADING_CONFIGURATION);
        assertThat(adoption).allSatisfy(entry -> {
            assertThat(entry.feature()).isNotNull();
            assertThat(entry.count()).isLessThanOrEqualTo(entry.total());
        });
    }

    private List<FeatureUsageTrendPointDTO> getTrend(String query) throws Exception {
        return request.get("/api/admin/feature-usage/trend?" + query, HttpStatus.OK, new TypeReference<>() {
        });
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

    private static UserFeatureUsageDTO featureOf(FeatureUsageOverviewDTO overview, UserFeature feature) {
        return overview.features().stream().filter(entry -> entry.feature() == feature).findFirst().orElseThrow();
    }

    private static FeatureUsageEntryDTO endpointOf(FeatureUsageOverviewDTO overview, TrackedFeature feature) {
        return overview.endpoints().stream().filter(entry -> entry.featureId() == feature.getId()).findFirst().orElseThrow();
    }
}
