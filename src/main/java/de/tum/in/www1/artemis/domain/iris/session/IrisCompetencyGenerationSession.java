package de.tum.in.www1.artemis.domain.iris.session;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("COMPETENCY_GENERATION")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class IrisCompetencyGenerationSession extends IrisSession {
    // TODO: either link competency recommendations OR just save as string.
    // TODO: problem is messages are not compatible with irisv2(?)
    // TODO: maybe link with a course?
}
