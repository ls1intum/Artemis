package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import de.tum.cit.aet.artemis.core.domain.FeatureInteraction;
import de.tum.cit.aet.artemis.core.domain.FeatureKind;
import de.tum.cit.aet.artemis.core.domain.TrackedFeature;
import de.tum.cit.aet.artemis.core.repository.TrackedFeatureRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

/**
 * Runs the startup inventory scan against the real controller set.
 * <p>
 * This is the part of the feature that everything else depends on: if the scan silently produced nothing, the page would
 * come up empty and no other test would notice, because the unit tests only cover how a single mapping is described and
 * the read API tests work from hand-written rows. It also pins the two properties that make the report trustworthy, namely
 * that a controller mapping a legacy alias is counted once and that a rescan does not duplicate anything.
 */
class FeatureUsageInventoryTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private FeatureUsageRegistry featureUsageRegistry;

    @Autowired
    private TrackedFeatureRepository trackedFeatureRepository;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    private Set<Long> preExistingFeatureIds;

    @BeforeEach
    void initTestCase() {
        preExistingFeatureIds = trackedFeatureRepository.findAll().stream().map(TrackedFeature::getId).collect(Collectors.toSet());
    }

    @AfterEach
    void tearDown() {
        List<TrackedFeature> written = trackedFeatureRepository.findAll().stream().filter(feature -> !preExistingFeatureIds.contains(feature.getId())).toList();
        trackedFeatureRepository.deleteAll(written);
    }

    @Test
    void shouldRegisterTheWholeApiAsRestFeatures() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        List<TrackedFeature> features = writtenFeatures();
        // Artemis exposes on the order of a thousand endpoints; the exact number moves with every release, so assert an
        // order of magnitude rather than a figure that would need updating constantly
        assertThat(features).hasSizeGreaterThan(500);
        assertThat(features).allSatisfy(feature -> {
            assertThat(feature.getFeatureKind()).isEqualTo(FeatureKind.REST);
            assertThat(feature.getModule()).isNotBlank();
            assertThat(feature.getIdentifier()).isNotBlank();
            assertThat(feature.getLastRegisteredAt()).isNotNull();
        });
    }

    @Test
    void shouldIdentifyEveryFeatureByItsVerbAndCanonicalApiPath() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        assertThat(writtenFeatures()).allSatisfy(feature -> assertThat(feature.getIdentifier()).matches("^[A-Z,]+ (api/|\\.well-known/).*"));
    }

    @Test
    void shouldCoverTheMainModules() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        assertThat(writtenFeatures()).extracting(TrackedFeature::getModule).contains("programming", "exercise", "course", "admin", "communication");
    }

    @Test
    void shouldCarryTheFeatureAllTheWayIntoTheDatabase() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // The page resolves the label back to a catalogue entry, so whatever is stored has to be the name of one.
        // Completeness of the catalogue itself is enforced by FeatureUsageAnnotationTest against the production controllers;
        // this context also contains a handful of test-only controllers, which are deliberately not catalogued.
        Set<String> catalogue = Arrays.stream(UserFeature.values()).map(UserFeature::name).collect(Collectors.toSet());
        assertThat(writtenFeatures()).filteredOn(feature -> feature.getFeatureLabel() != null).extracting(TrackedFeature::getFeatureLabel).allMatch(catalogue::contains);
        assertThat(writtenFeatures()).extracting(TrackedFeature::getFeatureLabel).contains(UserFeature.PROGRAMMING_GRADING_CONFIGURATION.name(),
                UserFeature.PROGRAMMING_SUBMISSION_POLICY.name(), UserFeature.PROGRAMMING_REPOSITORY_EDITING.name(), UserFeature.PROGRAMMING_ONLINE_IDE.name());
    }

    @Test
    void shouldLetAMethodOverrideTheFeatureOfItsController() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // re-evaluating every result of an exercise is assigned separately from the rest of its controller
        assertThat(writtenFeatures()).filteredOn(feature -> feature.getIdentifier().endsWith("/grading/re-evaluate")).extracting(TrackedFeature::getFeatureLabel)
                .containsExactly(UserFeature.PROGRAMMING_REEVALUATION.name());
    }

    @Test
    void shouldClassifyEveryEndpointAndNameItsResource() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        assertThat(writtenFeatures()).allSatisfy(feature -> {
            assertThat(feature.getInteraction()).isNotNull();
            assertThat(feature.getResource()).isNotBlank();
        });
        // one endpoint of each interaction the verb cannot tell on its own
        assertThat(interactionOf("GET api/course/courses/{courseId}/title")).isEqualTo(FeatureInteraction.AUTOMATIC);
        assertThat(interactionOf("POST api/exercise/problem-statement/render")).isEqualTo(FeatureInteraction.VIEW);
        assertThat(interactionOf("GET .well-known/apple-app-site-association")).isEqualTo(FeatureInteraction.SYSTEM);
        assertThat(interactionOf("GET api/course/courses/{courseId}/for-overview")).isEqualTo(FeatureInteraction.VIEW);
        assertThat(interactionOf("POST api/course/courses/{courseId}/enroll")).isEqualTo(FeatureInteraction.ACTION);
    }

    private FeatureInteraction interactionOf(String identifier) {
        return writtenFeatures().stream().filter(feature -> feature.getIdentifier().equals(identifier)).map(TrackedFeature::getInteraction).findFirst()
                .orElseThrow(() -> new AssertionError("no endpoint " + identifier + " in the inventory"));
    }

    /**
     * Some controllers map a canonical prefix plus one or more deprecated ones; if both were registered, the same feature would be split across two rows and neither would show
     * its real usage.
     * <p>
     * The legacy paths are read off the controllers rather than listed here. A hand-written list is wrong twice over: it goes stale the moment an alias is added or retired,
     * which is how {@code api/core/admin/} came to be guarded while {@code api/core/passkey/} never was, and it cannot express an alias whose prefix is also somebody else's
     * canonical prefix, as {@code api/core/} was for the course module while remaining the canonical prefix of {@code FileResource}.
     */
    @Test
    void shouldNotCountAControllerWithALegacyAliasTwice() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        Set<String> legacyPaths = legacyAliasPaths();
        // An assumption rather than an assertion, so that this reports as skipped rather than as a silent pass. The
        // aliases are being retired; the last one left, api/programming/public/ on PublicBuildPlanResource, sits behind
        // the Jenkins profile this context does not activate. Once it is gone the check below has nothing to catch and
        // saying so out loud is better than a green test that examined nothing.
        assumeThat(legacyPaths).as("the endpoints reachable under a legacy class level prefix").isNotEmpty();
        assertThat(writtenFeatures()).extracting(TrackedFeature::getIdentifier).doesNotHaveDuplicates()
                .noneMatch(identifier -> legacyPaths.contains(identifier.substring(identifier.indexOf(' ') + 1)));
    }

    /**
     * Every path that is only reachable because its controller declares a legacy alias next to its canonical prefix.
     * <p>
     * {@code FeatureUsageRegistry#canonicalPath} treats the first entry of a class level {@code @RequestMapping} as the canonical one, so this is the complement: the patterns
     * of a multi-entry mapping that do not sit under the first entry. Those are exactly the identifiers the inventory must never register.
     *
     * @return the legacy paths, without a leading slash, in the spelling the inventory would store them under
     */
    private Set<String> legacyAliasPaths() {
        Set<String> legacyPaths = new HashSet<>();
        requestMappingHandlerMapping.getHandlerMethods().forEach((mapping, handlerMethod) -> {
            RequestMapping classMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
            if (classMapping == null || classMapping.value().length < 2) {
                return;
            }
            String canonicalPrefix = withLeadingSlash(classMapping.value()[0]);
            List<String> aliases = Arrays.stream(classMapping.value()).skip(1).map(FeatureUsageInventoryTest::withLeadingSlash).toList();
            mapping.getPatternValues().stream().filter(pattern -> !withLeadingSlash(pattern).startsWith(canonicalPrefix))
                    .filter(pattern -> aliases.stream().anyMatch(alias -> withLeadingSlash(pattern).startsWith(alias)))
                    .forEach(pattern -> legacyPaths.add(withLeadingSlash(pattern).substring(1)));
        });
        return legacyPaths;
    }

    private static String withLeadingSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    @Test
    void shouldBeIdempotentAndAdvanceTheRegistrationTimestamp() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);
        int afterFirstScan = writtenFeatures().size();
        Instant beforeSecondScan = Instant.now();

        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // every node runs this on every startup, so a rescan must update rather than duplicate
        assertThat(writtenFeatures()).hasSize(afterFirstScan);
        assertThat(writtenFeatures()).allSatisfy(feature -> assertThat(feature.getLastRegisteredAt()).isAfterOrEqualTo(beforeSecondScan));
    }

    @Test
    void shouldResolveARegisteredEndpointBackToItsFeatureId() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // the request path does this lookup on every call, keyed on the reflected method
        var handlerMethod = requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("de.tum.cit.aet.artemis.")).findFirst().orElseThrow().getValue();

        assertThat(featureUsageRegistry.restFeatureId(handlerMethod.getMethod())).isNotNull();
    }

    private List<TrackedFeature> writtenFeatures() {
        return trackedFeatureRepository.findAll().stream().filter(feature -> !preExistingFeatureIds.contains(feature.getId())).toList();
    }
}
