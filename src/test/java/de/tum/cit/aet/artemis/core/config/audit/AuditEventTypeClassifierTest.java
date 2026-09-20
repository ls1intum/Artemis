package de.tum.cit.aet.artemis.core.config.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.core.config.Constants;

/**
 * Tests the taxonomy that decides which audit log an event type is written to. Getting this wrong silently misfiles
 * events and applies the wrong retention period to them, so each category is pinned explicitly.
 */
class AuditEventTypeClassifierTest {

    @ParameterizedTest
    @ValueSource(strings = { AuditEventConstants.AUTHENTICATION_SUCCESS, AuditEventConstants.AUTHENTICATION_PASSKEY_SUCCESS, AuditEventConstants.SAML2_AUTHENTICATION_SUCCESS,
            AuditEventConstants.AUTHENTICATION_FAILURE, AuditEventConstants.LOGOUT_SUCCESS })
    void authenticationEventsBelongToTheGeneralLog(String eventType) {
        assertThat(AuditEventTypeClassifier.classify(eventType)).isEqualTo(AuditLogType.GENERAL);
    }

    @ParameterizedTest
    @ValueSource(strings = { AuditEventConstants.PASSWORD_RESET_REQUESTED, AuditEventConstants.PASSWORD_RESET_REQUEST_REJECTED, AuditEventConstants.PASSWORD_RESET_COMPLETED,
            AuditEventConstants.ACCOUNT_EMAIL_CHANGED, AuditEventConstants.ACCOUNT_REGISTERED, AuditEventConstants.SAML2_ACCOUNT_CREATE,
            AuditEventConstants.AUTHENTICATION_SWITCH })
    void accountCredentialAndIdentityChangesBelongToTheSecurityLog(String eventType) {
        assertThat(AuditEventTypeClassifier.classify(eventType)).isEqualTo(AuditLogType.SECURITY);
    }

    @ParameterizedTest
    @ValueSource(strings = { Constants.ACTIVATE_USER, Constants.DEACTIVATE_USER })
    void accountStateChangesBelongToTheSecurityLog(String eventType) {
        // Deactivating an account deletes its passkeys, SSH keys and every VCS access token, and the revocation writes no
        // event of its own - so DEACTIVATE_USER is the only audit record of that. Falling through to the APPLICATION
        // default would hide it from the Security tab, which is where an investigation into account access looks.
        assertThat(AuditEventTypeClassifier.classify(eventType)).isEqualTo(AuditLogType.SECURITY);
    }

    @ParameterizedTest
    @ValueSource(strings = { Constants.DELETE_EXERCISE, Constants.EDIT_EXERCISE, Constants.DELETE_COURSE, Constants.RESET_COURSE, Constants.DELETE_EXAM, Constants.UPDATE_EXAM,
            Constants.RESET_EXAM, Constants.ADD_USER_TO_EXAM, Constants.REMOVE_USER_FROM_EXAM, Constants.ENROLL_IN_COURSE, Constants.UNENROLL_FROM_COURSE,
            Constants.DELETE_PARTICIPATION, Constants.DELETE_TEAM, Constants.IMPORT_TEAMS, Constants.RESET_GRADING, Constants.PREPARE_EXERCISE_START })
    void domainActionsBelongToTheApplicationLog(String eventType) {
        assertThat(AuditEventTypeClassifier.classify(eventType)).isEqualTo(AuditLogType.APPLICATION);
    }

    @Test
    void unknownAndNullTypesDefaultToTheApplicationLog() {
        // Defaulting to APPLICATION (five-year retention) means a newly added domain event is over-retained rather than
        // being dropped on the short general schedule, which is the safe direction if the record is needed later.
        assertThat(AuditEventTypeClassifier.classify("SOME_FUTURE_EVENT_NOBODY_CLASSIFIED")).isEqualTo(AuditLogType.APPLICATION);
        assertThat(AuditEventTypeClassifier.classify(null)).isEqualTo(AuditLogType.APPLICATION);
    }

    @Test
    void everyEventTypeSpringBootPublishesIsClassifiedDeliberately() {
        // These are the types Spring Boot's AuthenticationAuditListener and AuthorizationAuditListener can publish.
        // Any of them falling through to the APPLICATION default would give a login record five-year retention and hide
        // it from the Login tab, so each one is asserted explicitly rather than relying on the default.
        assertThat(AuditEventConstants.GENERAL_EVENT_TYPES).contains(AuditEventConstants.AUTHENTICATION_SUCCESS, AuditEventConstants.AUTHENTICATION_FAILURE,
                AuditEventConstants.LOGOUT_SUCCESS);
        assertThat(AuditEventConstants.SECURITY_EVENT_TYPES).contains(AuditEventConstants.AUTHENTICATION_SWITCH);
        assertThat(AuditEventTypeClassifier.classify(AuditEventConstants.AUTHORIZATION_FAILURE)).isEqualTo(AuditLogType.APPLICATION);
    }

    @Test
    void theGeneralAndSecurityTypeSetsAreDisjoint() {
        // An overlap would make classification order-dependent and the retention of those events ambiguous.
        assertThat(AuditEventConstants.GENERAL_EVENT_TYPES).doesNotContainAnyElementsOf(AuditEventConstants.SECURITY_EVENT_TYPES);
    }

    @Test
    void authorizationFailureIsNotPartOfAnyPersistedCategorySet() {
        // AUTHORIZATION_FAILURE is deliberately never persisted; it must not appear in a set that implies it is.
        assertThat(AuditEventConstants.GENERAL_EVENT_TYPES).doesNotContain(AuditEventConstants.AUTHORIZATION_FAILURE);
        assertThat(AuditEventConstants.SECURITY_EVENT_TYPES).doesNotContain(AuditEventConstants.AUTHORIZATION_FAILURE);
    }
}
