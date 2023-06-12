package de.tum.in.www1.artemis.domain.iris.settings;

import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;

/**
 * An IrisMessageContent represents a part of the content of an IrisMessage.
 * For now, IrisMessageContent only supports text content.
 * In the future, we might want to support images and other content types.
 */
@Entity
@Table(name = "iris_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSettings extends DomainObject {

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "iris_chat_settings_id")
    private IrisSubSettings irisChatSettings;

    @OneToOne(cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "iris_hestia_settings_id")
    private IrisSubSettings irisHestiaSettings;

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
}
