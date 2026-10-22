package de.tum.cit.aet.artemis.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.dto.ScienceCourseConsentDTO;
import de.tum.cit.aet.artemis.core.service.feature.Feature;

/**
 * Covers the role hierarchy declared in {@code SecurityConfiguration}, which is what lets a higher role satisfy the
 * {@code hasRole('USER')} expression behind {@code @EnforceAtLeastStudent}.
 * <p>
 * Spring Security applies the hierarchy by picking the {@code RoleHierarchy} bean up by type, without the application
 * configuring an expression handler of its own. Nothing else asserts that this still happens, and when it stops the
 * failure is silent at startup and only shows up as every teaching role losing access to student endpoints.
 * <p>
 * {@code GET api/atlas/science/consents} is the subject because it is a plain {@code @EnforceAtLeastStudent} endpoint
 * that needs no course, exercise or other precondition, so a 403 here can only come from the hierarchy.
 */
class RoleHierarchyIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "rolehierarchy";

    @BeforeEach
    void initTestCase() {
        featureToggleService.enableFeature(Feature.Science);
        userUtilService.addUsers(TEST_PREFIX, 0, 1, 0, 1);
    }

    @AfterEach
    void disableFeatureToggle() {
        featureToggleService.disableFeature(Feature.Science);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void tutorReachesAStudentLevelEndpoint() throws Exception {
        List<ScienceCourseConsentDTO> consents = request.getList("/api/atlas/science/consents", HttpStatus.OK, ScienceCourseConsentDTO.class);
        assertThat(consents).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void instructorReachesAStudentLevelEndpoint() throws Exception {
        List<ScienceCourseConsentDTO> consents = request.getList("/api/atlas/science/consents", HttpStatus.OK, ScienceCourseConsentDTO.class);
        assertThat(consents).isNotNull();
    }
}
