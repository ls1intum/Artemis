package de.tum.cit.aet.artemis.atlas.domain.science;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import de.tum.cit.aet.artemis.core.domain.AbstractAuditingEntity;

@Entity
@Table(name = "science_research_export_audit")
public class ScienceResearchExportAudit extends AbstractAuditingEntity {

    @Column(name = "purpose", nullable = false, length = 1000)
    private String purpose;

    @Lob
    @Column(name = "course_filter", nullable = false)
    private String courseFilter;

    @Column(name = "date_from")
    private String dateFrom;

    @Column(name = "date_to")
    private String dateTo;

    @Lob
    @Column(name = "event_types", nullable = false)
    private String eventTypes;

    @Column(name = "file_checksum")
    private String fileChecksum;

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getCourseFilter() {
        return courseFilter;
    }

    public void setCourseFilter(String courseFilter) {
        this.courseFilter = courseFilter;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(String eventTypes) {
        this.eventTypes = eventTypes;
    }

    public String getFileChecksum() {
        return fileChecksum;
    }

    public void setFileChecksum(String fileChecksum) {
        this.fileChecksum = fileChecksum;
    }
}
