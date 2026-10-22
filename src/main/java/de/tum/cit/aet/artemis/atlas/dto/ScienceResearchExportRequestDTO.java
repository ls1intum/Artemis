package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEventType;

// The elements are checked too: a null id or type would otherwise get past validation, have the whole CSV generated and
// only then fail while the audit is built, leaving an unaudited export behind.
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceResearchExportRequestDTO(@NotEmpty Set<@NotNull Long> courseIds, ZonedDateTime from, ZonedDateTime to, Set<@NotNull ScienceEventType> eventTypes,
        // Bounded to the column width: without it an over-long purpose generates the whole CSV and then fails on the
        // audit insert, which is both a 500 and an export nobody can account for.
        @NotBlank @Size(max = 1000) String purpose) {
}
