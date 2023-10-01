package de.tum.in.www1.artemis.domain.iris.settings;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

/**
 * An IrisSubSettings object represents the settings for a specific feature of Iris.
 * {@link IrisSettings} is the parent of this class.
 */
@Entity
@Table(name = "iris_sub_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSubSettings extends DomainObject {

    @Column(name = "enabled")
    private boolean enabled = false;

    @Nullable
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate template;

    @Nullable
    @Column(name = "preferredModel")
    private String preferredModel;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public IrisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(@Nullable IrisTemplate template) {
        this.template = template;
    }

    @Nullable
    public String getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(@Nullable String preferredModel) {
        this.preferredModel = preferredModel;
    }
}
