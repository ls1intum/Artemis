package de.tum.in.www1.artemis.domain.submissionpolicy;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.service.SubmissionPolicyService;

/**
 * Configures a Lock Repository Policy.<br>
 * The Lock Repository Policy locks a participation repository after the participant submits
 * {@link SubmissionPolicy#submissionLimit} amount of times.<br>
 * The number of submissions in one participation is determined based on multiple factors.
 * More information on submission counts can be found at {@link SubmissionPolicyService#getParticipationSubmissionCount(Participation)}.
 */
@Entity
@DiscriminatorValue("LRP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LockRepositoryPolicy extends SubmissionPolicy {

    @Override
    public String toString() {
        return "LockRepositoryPolicy{id=%d, submissionLimit=%d, active=%b}".formatted(getId(), getSubmissionLimit(), isActive());
    }
}
