package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An {@link IrisSubSettings} implementation for the settings for competency generation.
 */
@Entity
@DiscriminatorValue("COMPETENCY_GENERATION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCompetencyGenerationSubSettings extends IrisSubSettings {

}
