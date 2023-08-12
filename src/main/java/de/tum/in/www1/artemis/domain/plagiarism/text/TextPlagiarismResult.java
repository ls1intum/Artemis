package de.tum.in.www1.artemis.domain.plagiarism.text;

import javax.persistence.Entity;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;

/**
 * Result of the automatic plagiarism detection for text or programming exercises.
 */
@Entity
public class TextPlagiarismResult extends PlagiarismResult<TextSubmissionElement> {
}
