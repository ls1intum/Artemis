package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the specific ingestion sub-settings of lectures for IRIS.
 * This class extends {@link IrisSubSettings} to provide specialized settings required for lecture data ingestion.
 */
@Entity
@DiscriminatorValue("ingestion")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureIngestionSubSettings extends IrisSubSettings {
}
