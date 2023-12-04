package de.tum.in.www1.artemis.connectors;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.connector.AeolusRequestMockProvider;
import de.tum.in.www1.artemis.domain.enumeration.AeolusTarget;
import de.tum.in.www1.artemis.service.connectors.aeolus.AeolusBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.aeolus.Windfile;

class AeolusServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private AeolusRequestMockProvider aeolusRequestMockProvider;

    @Autowired
    @Qualifier("aeolusRestTemplate")
    RestTemplate restTemplate;

    @Value("${aeolus.url}")
    private URL aeolusUrl;

    @Autowired
    AeolusBuildPlanService aeolusBuildPlanService;

    /**
     * Initializes aeolusRequestMockProvider
     */
    @BeforeEach
    void init() {
        // Create apollonConversionService and inject @Value fields
        aeolusRequestMockProvider = new AeolusRequestMockProvider(restTemplate);
        ReflectionTestUtils.setField(aeolusRequestMockProvider, "aeolusUrl", aeolusUrl);

        aeolusRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        aeolusRequestMockProvider.reset();
    }

    /**
     * Publishes a build plan using Aeolus
     */
    @Test
    void testSuccessfulPublishBuildPlan() {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockSuccessfulPublishBuildPlan(AeolusTarget.BAMBOO, expectedPlanKey);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.BAMBOO);
        assertThat(key).isEqualTo(expectedPlanKey);
    }

    /**
     * Fails in publishing a build plan using Aeolus
     */
    @Test
    void testFailedPublishBuildPlan() {
        Windfile mockWindfile = new Windfile();
        var expectedPlanKey = "PLAN";
        mockWindfile.setId("PROJECT-" + expectedPlanKey);

        aeolusRequestMockProvider.mockFailedPublishBuildPlan(AeolusTarget.BAMBOO);
        String key = aeolusBuildPlanService.publishBuildPlan(mockWindfile, AeolusTarget.BAMBOO);
        assertThat(key).isEqualTo(null);
    }

}
