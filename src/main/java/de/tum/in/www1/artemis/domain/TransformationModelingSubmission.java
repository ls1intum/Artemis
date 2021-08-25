package de.tum.in.www1.artemis.domain;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;

@Entity
@DiscriminatorValue(value = "TM")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TransformationModelingSubmission extends ModelingSubmission {
}
