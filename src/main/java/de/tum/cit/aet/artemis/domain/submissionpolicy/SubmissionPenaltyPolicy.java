package de.tum.cit.aet.artemis.domain.submissionpolicy;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.domain.participation.Participation;
import de.tum.cit.aet.artemis.service.SubmissionPolicyService;

/**
 * Configures a Submission Penalty Policy.<br>
 * The Submission Penalty Policy imposes a permanent {@link SubmissionPenaltyPolicy#exceedingPenalty} on the achievable
 * participation score for every submission exceeding the {@link SubmissionPolicy#submissionLimit}. The {@link SubmissionPenaltyPolicy#exceedingPenalty}
 * increases with the submissions exceeding the limit in a linear way.<br>
 * The number of submissions in one participation is determined based on multiple factors.
 * More information on submission counts can be found at {@link SubmissionPolicyService#getParticipationSubmissionCount(Participation)}.
 */
@Entity
@DiscriminatorValue("SPP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class SubmissionPenaltyPolicy extends SubmissionPolicy {

    @Column(name = "exceeding_penalty")
    private Double exceedingPenalty;

    public Double getExceedingPenalty() {
        return exceedingPenalty;
    }

    public void setExceedingPenalty(Double exceedingPenalty) {
        this.exceedingPenalty = exceedingPenalty;
    }

    @Override
    public String toString() {
        return "SubmissionPenaltyPolicy{id=%d, submissionLimit=%d, active=%b, exceedingPenalty=%.2f}".formatted(getId(), getSubmissionLimit(), isActive(), getExceedingPenalty());
    }
}
