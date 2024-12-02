package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A region within an artifact where a result was detected.
 *
 * @param startLine   The line number of the first character in the region.
 * @param startColumn The column number of the first character in the region.
 * @param endLine     The line number of the last character in the region.
 * @param endColumn   The column number of the character following the end of the region.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Region(Integer startLine, Integer startColumn, Integer endLine, Integer endColumn) {

    /**
     * The line number of the first character in the region.
     */
    public Optional<Integer> getOptionalStartLine() {
        return Optional.ofNullable(startLine);
    }

    /**
     * The column number of the first character in the region.
     */
    public Optional<Integer> getOptionalStartColumn() {
        return Optional.ofNullable(startColumn);
    }

    /**
     * The line number of the last character in the region.
     */
    public Optional<Integer> getOptionalEndLine() {
        return Optional.ofNullable(endLine);
    }

    /**
     * The column number of the character following the end of the region.
     */
    public Optional<Integer> getOptionalEndColumn() {
        return Optional.ofNullable(endColumn);
    }

}
