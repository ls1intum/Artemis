package de.tum.in.www1.artemis;

import static org.mockito.Mockito.*;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.service.MailService;
import de.tum.in.www1.artemis.service.connectors.SAML2Service;
import de.tum.in.www1.artemis.service.connectors.jenkins.JenkinsProgrammingLanguageFeatureService;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.UserJWTController;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Test base for {@link UserJWTController#authorizeSAML2(String, HttpServletResponse)} and {@link SAML2Service}.
 *
 * @author Dominik Fuchss
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase
// NOTE: we use a common set of active profiles to reduce the number of application launches during testing. This significantly saves time and memory!
@ActiveProfiles({ SPRING_PROFILE_TEST, "artemis", "saml2" })
@TestPropertySource(properties = { "artemis.user-management.use-external=false" })
public abstract class AbstractSpringIntegrationSaml2Test {

    @Autowired
    protected DatabaseUtilService database;

    // NOTE: this has to be a MockBean, because the class cannot be instantiated in the tests
    @MockBean
    protected RelyingPartyRegistrationRepository relyingPartyRegistrationRepository;

    @SpyBean
    protected JenkinsProgrammingLanguageFeatureService programmingLanguageFeatureService;

    @SpyBean
    protected MailService mailService;

    @Autowired
    protected RequestUtilService request;

    @Autowired
    protected SAML2Service saml2Service;

    @BeforeEach
    void setUp() {
        doReturn(Map.of()).when(programmingLanguageFeatureService).getProgrammingLanguageFeatures();
        doReturn(null).when(programmingLanguageFeatureService).getProgrammingLanguageFeatures(any(ProgrammingLanguage.class));
        doReturn(null).when(relyingPartyRegistrationRepository).findByRegistrationId(anyString());
        doNothing().when(mailService).sendSAML2SetPasswordMail(any(User.class));
    }

    @AfterEach
    void resetSpyBeans() {
        Mockito.reset(relyingPartyRegistrationRepository, programmingLanguageFeatureService, mailService);
    }

}
