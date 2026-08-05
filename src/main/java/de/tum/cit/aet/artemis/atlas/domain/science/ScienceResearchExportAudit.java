package de.tum.cit.aet.artemis.atlas.domain.science;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;

@Entity
@Table(name = "science_research_export_audit")
public class ScienceResearchExportAudit extends AbstractAuditingEntity {

    @Column(name = "purpose", nullable = false, length = 1000)
    private String purpose;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter", nullable = false, columnDefinition = "json")
    private ScienceResearchExportFilter filter;

    @Column(name = "file_checksum")
    private String fileChecksum;

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public ScienceResearchExportFilter getFilter() {
        return filter;
    }

    public void setFilter(ScienceResearchExportFilter filter) {
        this.filter = filter;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }
}
