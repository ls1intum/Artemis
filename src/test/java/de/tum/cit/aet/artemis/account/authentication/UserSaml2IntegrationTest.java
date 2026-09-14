package de.tum.cit.aet.artemis.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2AssertionAuthentication;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertion;
import org.springframework.security.saml2.provider.service.authentication.Saml2ResponseAssertionAccessor;
import org.springframework.security.test.context.TestSecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.security.SAML2Service;
import de.tum.cit.aet.artemis.account.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.dto.vm.LoginVM;
import de.tum.cit.aet.artemis.core.web.open.PublicUserJwtResource;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalVCSamlTest;

/**
 * Tests for {@link PublicUserJwtResource} and {@link SAML2Service}.
 */
class UserSaml2IntegrationTest extends AbstractSpringIntegrationLocalVCSamlTest {

    private static final String STUDENT_NAME = "student_saml_test";

    /**
     * The same identifier as {@link #STUDENT_NAME}, but as an identity provider may well send it. The login is stored
     * lowercase, so this is what makes the difference between the derived and the stored value observable.
     */
    private static final String MIXED_CASE_STUDENT_NAME = "Student_SAML_Test";

    private static final String STUDENT_PASSWORD = "test1234";

    private static final String OTHER_STUDENT_NAME = "other_student_saml_test";

    private static final String STUDENT_REGISTRATION_NUMBER = "12345678";

    @Autowired
    private PasswordService passwordService;

    @AfterEach
    void clearExistingUser() {
        userTestRepository.findOneByLogin(STUDENT_NAME).ifPresent(userTestRepository::delete);
        userTestRepository.findOneByLogin(OTHER_STUDENT_NAME).ifPresent(userTestRepository::delete);
    }

    @AfterEach
    void clearAuthentication() {
        TestSecurityContextHolder.clearContext();
    }

    @Test
    void testAuthenticationRedirect() throws Exception {
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
        final String redirectTarget = request.getRedirectTarget("/saml2/authenticate", HttpStatus.FOUND);
        assertThat(redirectTarget).endsWith("/login");
    }

    /**
     * This test checks the creation of a new SAML2 authenticated user.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2Registration() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion(STUDENT_REGISTRATION_NUMBER));

        assertStudentExists();
        assertRegistrationNumber(STUDENT_REGISTRATION_NUMBER);
    }

    @Test
    void testSaml2RegistrationRejectsEmailUsedByAnotherAccount() throws Exception {
        assertStudentNotExists();
        User existingUser = new User();
        existingUser.setLogin(OTHER_STUDENT_NAME);
        existingUser.setActivated(true);
        existingUser.setEmail(STUDENT_NAME + "@invalid");
        userTestRepository.save(existingUser);

        mockSAMLAuthentication(createAssertion(STUDENT_REGISTRATION_NUMBER));
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.BAD_REQUEST);

        assertStudentNotExists();
    }

    /**
     * This test checks that a new SAMl2 user is created with the extracted registration number.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationExtractingRegistrationNumber() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion("somePrefix1234someSuffix"));

        assertStudentExists();
        assertRegistrationNumber("1234");
    }

    /**
     * This test checks that a new SAMl2 user is created with the complete attribute value even if no extraction was possible.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationNonMatchingRegistrationNumberExtraction() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion("nonMatchingRegNum"));

        assertStudentExists();
        assertRegistrationNumber("nonMatchingRegNum");
    }

    /**
     * This test checks that a new SAMl2 user is created with an empty registration number if the attribute is empty.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationEmptyRegistrationNumber() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion(""));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getRegistrationNumber()).isNull();
    }

    /**
     * This test checks the successful login of an existing user via SAML2.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2Login() throws Exception {
        assertStudentNotExists();

        // Other mail than in #createPrincipal for identification of user
        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";

        // Create User
        createUser(identifyingEmail);
        assertStudentExists();

        authenticate(createAssertion(STUDENT_REGISTRATION_NUMBER));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getEmail()).as("Email identifies already created user").isEqualTo(identifyingEmail);
    }

    /**
     * This test checks whether the user details are updated (first and last name) based on the SAML2 authentication.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testSaml2UpdateUserData() throws Exception {
        assertStudentNotExists();

        // Other mail than in #createPrincipal for identification of user
        String identifyingEmail = STUDENT_NAME + "@other.domain.invalid";

        // Create User
        createUser(identifyingEmail);
        assertStudentExists();

        authenticate(createAssertion(STUDENT_REGISTRATION_NUMBER));
        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getFirstName()).isEqualTo("FirstName");
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getLastName()).isEqualTo("LastName");

        // Use updated data for login
        authenticate(createAssertion(STUDENT_REGISTRATION_NUMBER, "NewFirstName", "NewLastName"));
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getFirstName()).isEqualTo("NewFirstName");
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getLastName()).isEqualTo("NewLastName");
    }

    /**
     * The attribute values come from the identity provider, so a name containing a regex replacement character must be
     * stored verbatim rather than read as a group reference.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RegistrationWithReplacementCharactersInAttributes() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion(STUDENT_REGISTRATION_NUMBER, "Ann$a", "O\\Brien"));

        assertStudentExists();
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getFirstName()).isEqualTo("Ann$a");
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getLastName()).isEqualTo("O\\Brien");
    }

    /**
     * The username pattern is filled from identity provider attributes, which may contain an uppercase letter, and the
     * login is stored lowercase. Both logins therefore have to resolve to the same account: without normalizing the
     * derived login the second one finds nothing, tries to create the user again and fails on the duplicate email.
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testValidSaml2RepeatedLoginWithMixedCaseUsernameAttribute() throws Exception {
        assertStudentNotExists();

        authenticate(createAssertion(MIXED_CASE_STUDENT_NAME, STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"));
        assertStudentExists();

        authenticate(createAssertion(MIXED_CASE_STUDENT_NAME, STUDENT_REGISTRATION_NUMBER, "FirstName", "LastName"));

        assertStudentExists();
        assertThat(userTestRepository.findAllByEmailOrUsernameIgnoreCase(STUDENT_NAME + "@invalid")).as("The second login reuses the account the first one created").hasSize(1);
    }

    /**
     * This test checks the successful login of an existing user via username and password (after creation via SAML2).
     *
     * @throws Exception if something went wrong.
     */
    @Test
    void testPasswordLoginAfterShibbolethRegistration() throws Exception {
        assertStudentNotExists();

        // Create user
        mockSAMLAuthentication();
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.OK);
        assertStudentExists();

        // Change Password
        User student = userTestRepository.findUserWithAuthoritiesByLogin(STUDENT_NAME).orElseThrow();
        student.setPassword(passwordService.hashPassword(STUDENT_PASSWORD));
        student.setInternal(true);
        userTestRepository.saveAndFlush(student);

        // Try to login ..
        TestSecurityContextHolder.clearContext();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.103 Safari/537.36");

        request.postWithoutResponseBody("/api/core/public/authenticate", createLoginVM(), HttpStatus.OK, httpHeaders);

        // Check SAML Login afterwards ..

        TestSecurityContextHolder.clearContext();
        // Mock existing SAML2 Auth
        mockSAMLAuthentication();
        // Test whether authorizeSAML2 generates a valid token
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.OK);
    }

    /**
     * This tests checks whether the access to the system is restricted if the login is not present.
     *
     * @throws Exception if something went wrong
     */
    @Test
    void testInvalidAuthenticationSaml2Login() throws Exception {
        assertStudentNotExists();
        // Test whether authorizeSAML2 generates a no token
        request.post("/api/core/public/saml2", Boolean.FALSE, HttpStatus.UNAUTHORIZED);
        assertStudentNotExists();
    }

    private void authenticate(Saml2ResponseAssertionAccessor assertion) throws Exception {
        mockSAMLAuthentication(assertion);
        request.postWithoutResponseBody("/api/core/public/saml2", Boolean.FALSE, HttpStatus.OK);
    }

    private void mockSAMLAuthentication() throws Exception {
        mockSAMLAuthentication(createAssertion(STUDENT_REGISTRATION_NUMBER));
    }

    /**
     * Builds the authentication Spring Security's SAML2 provider produces: a {@link Saml2AssertionAuthentication} whose
     * credentials are the accessor for the validated response. The production code reads the user attributes from that
     * accessor, so the stub has to carry them there rather than on the principal.
     */
    private void mockSAMLAuthentication(Saml2ResponseAssertionAccessor assertion) throws Exception {
        Authentication authentication = new Saml2AssertionAuthentication(assertion, List.of(), "artemis");
        TestSecurityContextHolder.setAuthentication(authentication);
    }

    private LoginVM createLoginVM() {
        LoginVM user = new LoginVM();
        user.setUsername(STUDENT_NAME);
        user.setPassword(STUDENT_PASSWORD);
        user.setRememberMe(true);
        return user;
    }

    private void createUser(String identifyingEmail) {
        User user = new User();
        user.setLogin(STUDENT_NAME);
        user.setActivated(true);
        user.setEmail(identifyingEmail);
        userTestRepository.save(user);
    }

    private Saml2ResponseAssertionAccessor createAssertion(String registrationNumber) {
        return createAssertion(registrationNumber, "FirstName", "LastName");
    }

    private Saml2ResponseAssertionAccessor createAssertion(String registrationNumber, String firstName, String lastName) {
        return createAssertion(STUDENT_NAME, registrationNumber, firstName, lastName);
    }

    /**
     * The email stays derived from {@link #STUDENT_NAME} regardless of the uid, so that two assertions differing only in
     * the case of the uid still describe the same person.
     */
    private Saml2ResponseAssertionAccessor createAssertion(String uid, String registrationNumber, String firstName, String lastName) {
        Map<String, List<Object>> attributes = new HashMap<>();
        attributes.put("uid", List.of(uid));
        attributes.put("first_name", List.of(firstName));
        attributes.put("last_name", List.of(lastName));
        attributes.put("email", List.of(STUDENT_NAME + "@invalid"));
        attributes.put("registration_number", List.of(registrationNumber));

        return Saml2ResponseAssertion.withResponseValue("response").nameId(uid).sessionIndexes(List.of()).attributes(attributes).build();
    }

    private void assertStudentNotExists() {
        assertThatIllegalArgumentException().isThrownBy(() -> userUtilService.getUserByLogin(STUDENT_NAME))
                .withMessage("Provided login " + STUDENT_NAME + " does not exist in database");
    }

    private void assertStudentExists() {
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME)).as("User shall exist").isNotNull();
    }

    private void assertRegistrationNumber(String registrationNumber) {
        assertThat(userUtilService.getUserByLogin(STUDENT_NAME).getRegistrationNumber()).isEqualTo(registrationNumber);
    }
}
