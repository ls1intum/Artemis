package de.tum.cit.aet.artemis.core.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

/**
 * How the translator answers a violation of the unique index on {@code jhi_user(email)}.
 * <p>
 * The availability check in {@code UserCreationService} reads before it writes, so it cannot see a competing request:
 * the index is what settles the conflict, by refusing the second write. Without the mapping below the account that
 * lost the race would be told the server broke, for the one outcome the client has always had a message for.
 * <p>
 * A plain unit test on purpose. The database raising the violation is covered by
 * {@code UserRepositoryTest.testEmailIsUniqueIgnoringCase}; what needs pinning here is the wording this classifies on,
 * which no test that goes through a database would state explicitly.
 */
class ExceptionTranslatorTest {

    private ExceptionTranslator translator;

    private ServletWebRequest request;

    @BeforeEach
    void init() {
        translator = new ExceptionTranslator();
        // Injected from `jhipster.clientApp.name` in a running application; only the alert headers read it.
        ReflectionTestUtils.setField(translator, "applicationName", "Artemis");
        request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/core/admin/users"));
    }

    /**
     * The two names are what Hibernate extracts on PostgreSQL and on MySQL, which qualifies the key with its table.
     */
    @ParameterizedTest
    @ValueSource(strings = { "jhi_user_email", "jhi_user.jhi_user_email" })
    void shouldAnswerADuplicateEmailWithTheEmailAlreadyUsedResponse(String constraintName) {
        ResponseEntity<ProblemDetail> response = translator.handleDataIntegrityViolationException(violation(constraintName), request);

        EmailAlreadyUsedException expected = new EmailAlreadyUsedException();
        assertThat(response.getStatusCode()).isEqualTo(expected.getStatusCode());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTitle()).isEqualTo(expected.getBody().getTitle());
        assertThat(response.getHeaders().getFirst("X-Artemis-error")).isEqualTo("error." + expected.getErrorKey());
        assertThat(response.getHeaders().getFirst("X-Artemis-params")).isEqualTo(expected.getEntityName());
    }

    /**
     * Anything else stays a server error. Mapping every integrity violation to a client error would report a genuine
     * bug as the caller's fault.
     */
    @Test
    void shouldAnswerAnyOtherIntegrityViolationWithAServerError() {
        ResponseEntity<ProblemDetail> response = translator.handleDataIntegrityViolationException(violation("fk_result_submission"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * The driver puts the value that collided into the message, so a login that reads like the index name must not be
     * enough to be answered as a duplicate email. This is the message MySQL reports for it.
     */
    @Test
    void shouldNotAnswerADuplicateLoginThatLooksLikeTheIndexNameAsADuplicateEmail() {
        DataIntegrityViolationException duplicateLogin = new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", new SQLException("Duplicate entry 'jhi_user_email' for key 'jhi_user.login'"), "jhi_user.login"));

        ResponseEntity<ProblemDetail> response = translator.handleDataIntegrityViolationException(duplicateLogin, request);

        assertThat(response.getStatusCode()).as("a duplicate login is not a duplicate email").isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * The constraint name never reaches the translator on the exception it handles, only on a Hibernate cause below it,
     * which is the shape a real violation has — see {@code UserRepositoryTest.testEmailIsUniqueIgnoringCase}.
     */
    private static DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException("could not execute statement",
                new ConstraintViolationException("could not execute statement", new SQLException("constraint violated"), constraintName));
    }
}
