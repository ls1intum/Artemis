package de.tum.cit.aet.artemis.deimos.dto;

import org.jspecify.annotations.Nullable;

/**
 * One piece of evidence that was left out of the model's payload.
 *
 * @param reason   the limit that caused the omission
 * @param filePath the affected file, escaped and shortened for display, or {@code null} when a whole snapshot is affected
 * @param snapshot the affected snapshot (abbreviated commit hash and submission date), or {@code null} for the final
 *                     state compared against the exercise template
 */
public record DeimosOmission(DeimosOmissionReason reason, @Nullable String filePath, @Nullable String snapshot) {
}
