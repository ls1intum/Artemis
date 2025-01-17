package de.tum.cit.aet.artemis.iris.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the specific ingestion sub-settings of faqs for Iris.
 * This class extends {@link IrisSubSettings} to provide settings required for faq data ingestion.
 */
@Entity
@DiscriminatorValue("FAQ_INGESTION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisFaqIngestionSubSettings extends IrisSubSettings {

    @Column(name = "auto_ingest_on_faq_creation")
    private boolean autoIngestOnFaqCreation;

    public boolean getAutoIngestOnFaqCreation() {
        return autoIngestOnFaqCreation;
    }

    public void setAutoIngestOnFaqCreation(boolean autoIngestOnFaqCreation) {
        this.autoIngestOnFaqCreation = autoIngestOnFaqCreation;
    }

}
