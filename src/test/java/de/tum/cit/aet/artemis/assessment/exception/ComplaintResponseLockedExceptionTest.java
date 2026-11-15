package de.tum.cit.aet.artemis.assessment.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.assertj.core.api.InstanceOfAssertFactories;
import org.assertj.core.api.MapAssert;
import org.junit.jupiter.api.Test;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.ComplaintResponse;

class ComplaintResponseLockedExceptionTest {

    private static ComplaintResponse responseCreatedAt(Instant createdDate) {
        var reviewer = new User();
        reviewer.setLogin("reviewer1");
        var response = new ComplaintResponse();
        response.setReviewer(reviewer);
        response.setCreatedDate(createdDate);
        return response;
    }

    private static MapAssert<Object, Object> assertParamsOf(ComplaintResponseLockedException exception) {
        var properties = exception.getBody().getProperties();
        assertThat(properties).isNotNull();
        return assertThat(properties.get("params")).asInstanceOf(InstanceOfAssertFactories.MAP);
    }

    @Test
    void parameters_containReviewerAndLockEnd() {
        assertParamsOf(new ComplaintResponseLockedException(responseCreatedAt(Instant.parse("2030-01-01T10:00:00Z")))).containsEntry("user", "reviewer1").containsKey("lockEnd");
    }

    @Test
    void parameters_omitLockEndWhenTheResponseHasNoCreationDate() {
        assertParamsOf(new ComplaintResponseLockedException(responseCreatedAt(null))).containsEntry("user", "reviewer1").doesNotContainKey("lockEnd");
    }
}
