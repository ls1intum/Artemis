package de.tum.in.www1.artemis.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.security.ArtemisInternalAuthenticationProvider;
import de.tum.in.www1.artemis.service.UserService;

@ActiveProfiles({ "artemis", "internalAuth" })
public class InternalLtiAuthenticationIntegrationTest extends AuthenticationIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ArtemisInternalAuthenticationProvider artemisInternalAuthenticationProvider;

    private User student;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();

        database.addUsers(1, 0, 0);
        student = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        final var encrPassword = userService.passwordEncoder().encode("0000");
        student.setPassword(encrPassword);
        userRepository.save(student);
        ltiLaunchRequest.setLis_person_contact_email_primary(student.getEmail());
    }

    @Override
    @Test
    public void launchLtiRequest_authViaEmail_success() throws Exception {
        super.launchLtiRequest_authViaEmail_success();

        final var updatedStudent = userRepository.findOneWithGroupsAndAuthoritiesByLogin("student1").get();
        assertThat(student).isEqualTo(updatedStudent);
    }

    @Test
    @WithAnonymousUser
    public void authenticateAfterLtiRequest_success() throws Exception {
        super.launchLtiRequest_authViaEmail_success();

        final var auth = new TestingAuthenticationToken(student.getLogin(), "0000");
        final var authResponse = artemisInternalAuthenticationProvider.authenticate(auth);

        assertThat(authResponse.getCredentials().toString()).isEqualTo(student.getPassword());
        assertThat(authResponse.getPrincipal()).isEqualTo(student.getLogin());
    }
}
