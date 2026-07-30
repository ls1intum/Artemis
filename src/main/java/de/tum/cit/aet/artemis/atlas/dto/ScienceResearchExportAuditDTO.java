package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportAudit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceResearchExportAuditDTO(Long id, Instant createdDate, String createdBy, String purpose, String courseFilter, String dateFrom, String dateTo, String eventTypes,
        String fileChecksum) {

    public static ScienceResearchExportAuditDTO of(ScienceResearchExportAudit audit) {
        return new ScienceResearchExportAuditDTO(audit.getId(), audit.getCreatedDate(), audit.getCreatedBy(), audit.getPurpose(), audit.getCourseFilter(), audit.getDateFrom(),
                audit.getDateTo(), audit.getEventTypes(), audit.getFileChecksum());
    }
}
