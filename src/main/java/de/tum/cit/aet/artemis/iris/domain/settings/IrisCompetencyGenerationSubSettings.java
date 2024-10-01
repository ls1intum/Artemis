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
 * An {@link IrisSubSettings} implementation for the settings for competency generation.
 * CompetencyGeneration settings provide a single {@link IrisTemplate}
 */
@Entity
@DiscriminatorValue("COMPETENCY_GENERATION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCompetencyGenerationSubSettings extends IrisSubSettings {

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
