package de.tum.in.www1.artemis.domain.hestia;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.SecondaryTable;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A TextHint.
 */
@Entity
@DiscriminatorValue("T")
@SecondaryTable(name = "text_hint")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TextHint extends ExerciseHint {
}
