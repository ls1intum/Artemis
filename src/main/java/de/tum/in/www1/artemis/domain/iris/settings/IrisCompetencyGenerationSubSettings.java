package de.tum.in.www1.artemis.domain.iris.settings;

import javax.annotation.Nullable;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;

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
