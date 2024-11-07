package de.tum.cit.aet.artemis.iris.domain.settings;

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
