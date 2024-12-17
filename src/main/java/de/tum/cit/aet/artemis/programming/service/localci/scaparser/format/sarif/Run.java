package de.tum.cit.aet.artemis.programming.service.localci.scaparser.format.sarif;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Describes a single run of an analysis tool, and contains the reported output of that run.
 *
 * @param tool    The analysis tool that was run.
 *                    (Required)
 * @param results The set of results contained in an SARIF log. The results array can be omitted when a run is solely exporting rules metadata. It must be present (but may be
 *                    empty) if a log file represents an actual scan.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Run(Tool tool, List<Result> results) {

    /**
     * The set of results contained in an SARIF log. The results array can be omitted when a run is solely exporting rules metadata. It must be present (but may be empty) if a log
     * file represents an actual scan.
     */
    public Optional<List<Result>> getOptionalResults() {
        return Optional.ofNullable(results);
    }

}
