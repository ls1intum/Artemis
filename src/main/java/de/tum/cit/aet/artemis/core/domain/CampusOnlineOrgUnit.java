package de.tum.cit.aet.artemis.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * JPA entity representing a CAMPUSOnline organizational unit.
 * <p>
 * An organizational unit (Org Unit) maps to a faculty, department, or institute in CAMPUSOnline.
 * Each org unit has a unique external ID used to query courses from the CAMPUSOnline API
 * and a human-readable name for display in the admin UI.
 */
@Entity
@Table(name = "campus_online_org_unit")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CampusOnlineOrgUnit extends DomainObject {

    /** The external numeric ID of the organizational unit in CAMPUSOnline (unique constraint). */
    @NotBlank
    @Size(max = 50)
    @Column(name = "external_id", nullable = false, unique = true, length = 50)
    private String externalId;

    /** The human-readable name of the organizational unit (e.g. "Department of Informatics"). */
    @NotBlank
    @Size(max = 255)
    @Column(name = "name", nullable = false)
    private String name;

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
