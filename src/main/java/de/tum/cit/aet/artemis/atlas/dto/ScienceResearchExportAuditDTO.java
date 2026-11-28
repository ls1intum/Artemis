package de.tum.cit.aet.artemis.atlas.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportAudit;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceResearchExportFilter;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ScienceResearchExportAuditDTO(Long id, Instant createdDate, String createdBy, String purpose, ScienceResearchExportFilter filter, String fileChecksum) {

    public static ScienceResearchExportAuditDTO of(ScienceResearchExportAudit audit) {
        return new ScienceResearchExportAuditDTO(audit.getId(), audit.getCreatedDate(), audit.getCreatedBy(), audit.getPurpose(), audit.getFilter(), audit.getFileChecksum());
    }
}
