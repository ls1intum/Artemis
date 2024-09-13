package de.tum.cit.aet.artemis.atlas.domain.science;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;

/**
 * Individual Science Setting which combined make the Science Settings (inside the hierarchical structure on the client side)
 * The unique constraint is needed to avoid duplications.
 */
@Entity
@Table(name = "science_setting", uniqueConstraints = { @UniqueConstraint(columnNames = { "user_id", "setting_id" }) })
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ScienceSetting extends DomainObject {

    @Column(name = "setting_id", nullable = false)
    private String settingId;

    @Column(name = "active", nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY)
    @JsonIgnoreProperties("scienceSetting")
    private User user;

    public ScienceSetting() {
        // Default empty constructor
    }

    public ScienceSetting(User user, String settingId, boolean active) {
        this.setUser(user);
        this.setSettingId(settingId);
        this.setActive(active);
    }

    public String getSettingId() {
        return settingId;
    }

    public void setSettingId(String settingId) {
        this.settingId = settingId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public String toString() {
        return "ScienceSetting{settingId='" + settingId + ", active=" + active + ", user=" + user + '}';
    }
}
