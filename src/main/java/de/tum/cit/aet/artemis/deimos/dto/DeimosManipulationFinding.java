package de.tum.cit.aet.artemis.deimos.dto;

import org.jspecify.annotations.Nullable;

/**
 * One place in a submission that imitates a Deimos fence marker.
 *
 * @param filePath   the path of the file, escaped and shortened for display
 * @param lineNumber the 1-based line of the marker, or {@code null} when the file path itself contains the marker
 * @param snapshot   the snapshot in which the marker first appeared (abbreviated commit hash and submission date)
 */
public record DeimosManipulationFinding(String filePath, @Nullable Integer lineNumber, String snapshot) {
}
