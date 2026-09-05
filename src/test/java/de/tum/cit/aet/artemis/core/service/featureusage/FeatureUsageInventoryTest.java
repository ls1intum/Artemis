package de.tum.cit.aet.artemis.core.service.featureusage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
    void shouldCarryTheAreaAndFeatureAllTheWayIntoTheDatabase() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // The page splits the label on the slash to build the tree, so whatever is stored has to be "area/feature".
        // Completeness of the catalogue itself is enforced by FeatureUsageCatalogueTest against the production controllers;
        // this context also contains a handful of test-only controllers, which are deliberately not catalogued.
        assertThat(writtenFeatures()).filteredOn(feature -> feature.getFeatureLabel() != null).extracting(TrackedFeature::getFeatureLabel)
                .allMatch(label -> label.matches("[a-z0-9-]+/[a-z0-9-]+"));
        assertThat(writtenFeatures()).extracting(TrackedFeature::getFeatureLabel).contains("configuration/static-code-analysis", "configuration/submission-policy",
                "configuration/auxiliary-repositories", "participation/online-ide");
    }

    @Test
    void shouldLetAMethodOverrideTheCatalogueForItsOwnFeature() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // re-evaluating every result of an exercise is annotated separately from the rest of its controller
        assertThat(writtenFeatures()).extracting(TrackedFeature::getFeatureLabel).contains("configuration/re-evaluate-results");
    }

    @Test
    void shouldNotCountAControllerWithALegacyAliasTwice() {
        featureUsageRegistry.registerEndpoints(requestMappingHandlerMapping);

        // Some controllers map a canonical prefix plus a deprecated one; if both were registered, the same feature would be
        // split across two rows and neither would show its real usage
        assertThat(writtenFeatures()).extracting(TrackedFeature::getIdentifier).doesNotHaveDuplicates().noneMatch(identifier -> identifier.contains("api/core/admin/"));
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
