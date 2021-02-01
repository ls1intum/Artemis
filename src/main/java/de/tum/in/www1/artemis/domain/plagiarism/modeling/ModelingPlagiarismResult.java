package de.tum.in.www1.artemis.domain.plagiarism.modeling;

import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
@Entity
public class ModelingPlagiarismResult extends PlagiarismResult<ModelingSubmissionElement> {

}
