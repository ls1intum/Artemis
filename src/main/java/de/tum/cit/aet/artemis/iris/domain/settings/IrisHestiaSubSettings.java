package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.annotation.Nullable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToOne;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.IrisTemplate;

/**
 * An {@link IrisSubSettings} implementation for the Hestia integration settings.
 * Hestia settings provide a single {@link IrisTemplate} for the hestia code hint generation requests.
 */
@Entity
@DiscriminatorValue("HESTIA")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisHestiaSubSettings extends IrisSubSettings {

    @Nullable
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private IrisTemplate template;

    @Nullable
    public IrisTemplate getTemplate() {
        return template;
    }

    public void setTemplate(@Nullable IrisTemplate template) {
        this.template = template;
    }
}
