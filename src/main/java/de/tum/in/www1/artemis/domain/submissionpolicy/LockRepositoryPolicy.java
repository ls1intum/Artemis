package de.tum.in.www1.artemis.domain.submissionpolicy;

import com.fasterxml.jackson.annotation.JsonInclude;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LRP")
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class LockRepositoryPolicy extends SubmissionPolicy {
}
