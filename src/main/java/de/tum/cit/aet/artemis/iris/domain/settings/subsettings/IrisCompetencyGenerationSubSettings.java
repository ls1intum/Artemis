package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * An {@link IrisSubSettings} implementation for the settings for competency generation.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisCompetencyGenerationSubSettings extends IrisSubSettings {

}
