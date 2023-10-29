package de.tum.in.www1.artemis.web.rest.dto.plagiarism;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismResult;
import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismSubmissionElement;
import de.tum.in.www1.artemis.web.rest.TextExerciseResource;

public record PlagiarismResultDTO<T extends PlagiarismResult<? extends PlagiarismSubmissionElement>> (T plagiarismResult,
        TextExerciseResource.PlagiarismResultStats plagiarismResultStats) {
}
