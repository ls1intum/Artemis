package de.tum.in.www1.artemis.domain.submissionpolicy;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import com.fasterxml.jackson.annotation.JsonInclude;

@Entity
@DiscriminatorValue("LRP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LockRepositoryPolicy extends SubmissionPolicy {
}
