package de.tum.cit.aet.artemis.iris.domain.settings.subsettings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;

/**
 * Represents the specific ingestion sub-settings of faqs for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for faq data ingestion.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IrisFaqIngestionSubSettings extends IrisSubSettings {

    private boolean autoIngestOnFaqCreation = true;

    public boolean getAutoIngestOnFaqCreation() {
        return autoIngestOnFaqCreation;
    }

    public void setAutoIngestOnFaqCreation(boolean autoIngestOnFaqCreation) {
        this.autoIngestOnFaqCreation = autoIngestOnFaqCreation;
    }

}
