package de.tum.cit.aet.artemis.plagiarism.domain.modeling;

import jakarta.persistence.Entity;

import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for modeling exercises.
 */
@Entity
public class ModelingPlagiarismResult extends PlagiarismResult<ModelingSubmissionElement> {

}
