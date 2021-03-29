package de.tum.in.www1.artemis;

import static io.github.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.connector.saml2.Saml2MockProvider;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "gitlab", "jenkins", "athene", "scheduling", "saml2" })
@TestPropertySource(properties = { "info.guided-tour.course-group-tutors=", "info.guided-tour.course-group-students=artemis-artemistutorial-students",
        "info.guided-tour.course-group-instructors=artemis-artemistutorial-instructors", "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationJenkinsGitlabSaml2Test {

    // TODO: define all spys here
    @Autowired
    protected DatabaseUtilService database;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected Saml2MockProvider saml2MockProvider;

    @AfterEach
    public void resetSpyBeans() {
        this.saml2MockProvider.resetSpyBeans();
    }
}
