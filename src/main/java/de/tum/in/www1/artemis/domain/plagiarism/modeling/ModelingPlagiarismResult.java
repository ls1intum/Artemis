package de.tum.in.www1.artemis.domain.plagiarism.modeling;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import jakarta.persistence.Entity;

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
@Entity
public class ModelingPlagiarismResult extends PlagiarismResult<ModelingSubmissionElement> {

}
