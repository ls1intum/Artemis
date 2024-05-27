package de.tum.in.www1.artemis.domain.iris.settings;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("ingestion")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisLectureIngestionSubSettings extends IrisSubSettings {
}
