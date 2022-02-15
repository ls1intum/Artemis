package de.tum.in.www1.artemis;

import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.service.programming.ProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.UserJWTController;

/**
 * Test base for {@link UserJWTController#authorizeSAML2(String)} and {@link SAML2Service}.
 *
 * @author Dominik Fuchss
 */
@SpringBootTest(properties = { "artemis.athene.token-validity-in-seconds=10800",
        "artemis.athene.base64-secret=YWVuaXF1YWRpNWNlaXJpNmFlbTZkb283dXphaVF1b29oM3J1MWNoYWlyNHRoZWUzb2huZ2FpM211bGVlM0VpcAo=" })
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!

@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "saml2" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationSaml2Test {

    @Autowired
    protected DatabaseUtilService database;

    @MockBean
    protected RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @MockBean
    protected ProgrammingLanguageFeatureService programmingMock;

    @MockBean
    protected MailService mailService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected SAML2Service saml2Service;

    @BeforeEach
    public void setUp() throws Exception {
        doReturn(Map.of()).when(programmingMock).getProgrammingLanguageFeatures();
        doReturn(null).when(programmingMock).getProgrammingLanguageFeatures(any(ProgrammingLanguage.class));
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
        doNothing().when(mailService).sendSAML2SetPasswordMail(any(User.class));
    }

    @AfterEach
    public void resetMockBeans() {
        Mockito.reset(relyingPartyRegistrationRepository, programmingMock, mailService);
    }

}
