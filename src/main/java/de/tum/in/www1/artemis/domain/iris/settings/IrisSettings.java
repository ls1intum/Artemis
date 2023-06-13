package de.tum.in.www1.artemis.domain.iris.settings;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.ColumnDefault;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisSettings object represents the settings for Iris for a part of Artemis.
 * These settings can be either global, course or exercise specific.
 * {@link de.tum.in.www1.artemis.service.iris.IrisSettingsService} for more details how IrisSettings are used.
 */
@Entity
@Table(name = "iris_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSettings extends DomainObject {

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "iris_hestia_settings_id")
    private IrisSubSettings irisHestiaSettings;

    @Column(name = "is_global")
    @ColumnDefault("false")
    private boolean isGlobal = false;

    public IrisSubSettings getIrisChatSettings() {
        return irisChatSettings;
    }

    public void setIrisChatSettings(IrisSubSettings irisChatSettings) {
        this.irisChatSettings = irisChatSettings;
    }

    public IrisSubSettings getIrisHestiaSettings() {
        return irisHestiaSettings;
    }

    public void setIrisHestiaSettings(IrisSubSettings irisHestiaSettings) {
        this.irisHestiaSettings = irisHestiaSettings;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }
}
