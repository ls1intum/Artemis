package de.tum.in.www1.artemis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.connector.AthenaRequestMockProvider;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;

/**
 * Base class for Athena tests providing common functionality
 */
public abstract class AbstractAthenaTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Value("${artemis.athena.url}")
    protected String athenaUrl;

    @Autowired
    protected AthenaRequestMockProvider athenaRequestMockProvider;

    @BeforeEach
    protected void initTestCase() {
        athenaRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        athenaRequestMockProvider.reset();
    }

    /**
     * Create an example text exercise with feedback suggestions enabled
     */
    protected TextExercise createTextExercise() {
        TextExercise textExercise = new TextExercise();
        textExercise.setId(1L);
        textExercise.setTitle("Test Exercise");
        textExercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        textExercise.setMaxPoints(10.0);
        return textExercise;
    }
}
