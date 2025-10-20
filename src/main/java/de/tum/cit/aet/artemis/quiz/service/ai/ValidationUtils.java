package de.tum.cit.aet.artemis.quiz.service.ai;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.quiz.service.ai.dto.AiQuestionSubtype;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.GeneratedMcQuestionDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.McOptionDTO;
import de.tum.cit.aet.artemis.quiz.service.ai.dto.ValidationSummaryDTO;

public final class ValidationUtils {

    static ValidationSummaryDTO basic(List<McOptionDTO> opts, AiQuestionSubtype subtype) {
        boolean hasSolution = opts.stream().anyMatch(McOptionDTO::correct);
        boolean hasHint = true; // stub fills a hint
        boolean diffOk = true;
        var issues = new ArrayList<String>();

        if (subtype == AiQuestionSubtype.SINGLE_CORRECT && opts.stream().filter(McOptionDTO::correct).count() != 1)
            issues.add("SINGLE_CORRECT must have exactly 1 correct option");

        if (subtype == AiQuestionSubtype.TRUE_FALSE) {
            if (opts.size() != 2)
                issues.add("TRUE_FALSE must have exactly 2 options");
            if (opts.stream().filter(McOptionDTO::correct).count() != 1)
                issues.add("TRUE_FALSE must have exactly 1 correct option");
        }

        return new ValidationSummaryDTO(hasSolution, hasHint, diffOk, issues);
    }

    static void enforceSubtypeConstraints(GeneratedMcQuestionDTO q) {
        var v = basic(q.options(), q.subtype());
        if (!v.issues().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, String.join("; ", v.issues()));
    }
}
