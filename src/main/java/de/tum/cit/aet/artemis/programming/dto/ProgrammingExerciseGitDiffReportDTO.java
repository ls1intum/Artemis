package de.tum.cit.aet.artemis.programming.dto;

import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffReport;

/**
 * DTO for a git diff report.
 *
 * @param entries DTO objects of the entries of the report
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ProgrammingExerciseGitDiffReportDTO(Set<ProgrammingExerciseGitDiffEntryDTO> entries) {

    public ProgrammingExerciseGitDiffReportDTO(ProgrammingExerciseGitDiffReport report) {
        this(report.getEntries().stream().map(ProgrammingExerciseGitDiffEntryDTO::new).collect(Collectors.toSet()));
    }
}
