package de.tum.cit.aet.artemis.hyperion.protocol;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Frozen candidate and trusted-supervisor verdict; only core decides whether a run may persist. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record GenerationOutput(WorkspaceSnapshot candidate, VerificationResult verification, @Nullable String verifiedDigest, SpecFidelityReport review, String terminationReason,
        @Nullable GenerationUsage usage, AccountingState accountingState, String effortProfile) {

    public GenerationOutput {
        // NON_EMPTY omits the empty profile; an absent wire value selects deployment defaults.
        effortProfile = effortProfile == null ? "" : effortProfile;
        Objects.requireNonNull(candidate);
        Objects.requireNonNull(review);
        Objects.requireNonNull(verification);
        Objects.requireNonNull(accountingState);
        if (verification.mechanicallyVerified() && !candidate.sha256().equals(verifiedDigest)) {
            throw new IllegalArgumentException("Verification must refer to the exact frozen candidate");
        }
        if (terminationReason == null || terminationReason.length() > 128 || effortProfile.length() > 64 || (accountingState == AccountingState.COMPLETE && usage == null)) {
            throw new IllegalArgumentException("Invalid generation outcome or accounting seal");
        }
    }

    /** Completeness of observed provider spend, independent of success or failure. */
    public enum AccountingState {
        PENDING, COMPLETE, INCOMPLETE
    }
}
