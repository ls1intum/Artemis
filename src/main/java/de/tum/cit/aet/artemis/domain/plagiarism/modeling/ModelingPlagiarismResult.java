package de.tum.cit.aet.artemis.domain.plagiarism.modeling;

import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
@Entity
public class ModelingPlagiarismResult extends PlagiarismResult<ModelingSubmissionElement> {

}
