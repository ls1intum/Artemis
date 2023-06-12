package de.tum.in.www1.artemis.domain.iris.settings;

import javax.annotation.Nullable;
import javax.persistence.*;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.DomainObject;
import de.tum.in.www1.artemis.domain.iris.session.IrisChatSession;
import de.tum.in.www1.artemis.domain.iris.session.IrisHestiaSession;
import de.tum.in.www1.artemis.service.connectors.iris.IrisModel;

/**
 * An IrisSession represents a list of messages of Artemis, a user, and an LLM.
 * See {@link IrisChatSession} and {@link IrisHestiaSession} for concrete implementations.
 */
@Entity
@Table(name = "iris_sub_settings")
@Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisSubSettings extends DomainObject {

    @Column(name = "enabled")
    private boolean enabled = false;

    @Nullable
    @Column(name = "externalTemplateId")
    private Long externalTemplateId;

    @Nullable
    @Column(name = "preferredModel")
    @Enumerated(EnumType.STRING)
    private IrisModel preferredModel;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public Long getExternalTemplateId() {
        return externalTemplateId;
    }

    public void setExternalTemplateId(@Nullable Long externalTemplateId) {
        this.externalTemplateId = externalTemplateId;
    }

    @Nullable
    public IrisModel getPreferredModel() {
        return preferredModel;
    }

    public void setPreferredModel(@Nullable IrisModel preferredModel) {
        this.preferredModel = preferredModel;
    }
}
