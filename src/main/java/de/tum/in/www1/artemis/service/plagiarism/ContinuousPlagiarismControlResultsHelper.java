package de.tum.in.www1.artemis.service.plagiarism;

import static de.tum.in.www1.artemis.service.plagiarism.ContinuousPlagiarismControlFeedbackHelper.isCpcFeedback;
import static java.util.Collections.emptySet;

import java.util.*;

import javax.annotation.Nullable;

import de.tum.in.www1.artemis.domain.Result;

public class ContinuousPlagiarismControlResultsHelper {

    public static boolean isCpcResult(@Nullable Result result) {
        return Optional.ofNullable(result).filter(it -> it.getFeedbacks().size() == 1).filter(it -> isCpcFeedback(it.getFeedbacks().get(0))).isPresent();
    }

    public static boolean containsCpcResult(@Nullable Collection<Result> results) {
        return Optional.ofNullable(results).orElse(emptySet()).stream().anyMatch(ContinuousPlagiarismControlResultsHelper::isCpcResult);
    }
}
