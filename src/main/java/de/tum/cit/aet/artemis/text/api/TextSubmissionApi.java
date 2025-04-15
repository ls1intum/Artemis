package de.tum.cit.aet.artemis.text.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

@Conditional(TextEnabled.class)
@Controller
public class TextSubmissionApi extends AbstractTextApi {

    private final TextSubmissionRepository textSubmissionRepository;

    private final TextSubmissionService textSubmissionService;

    public TextSubmissionApi(TextSubmissionRepository textSubmissionRepository, TextSubmissionService textSubmissionService) {
        this.textSubmissionRepository = textSubmissionRepository;
        this.textSubmissionService = textSubmissionService;
    }

    public TextSubmission findByIdElseThrow(long id) {
        return textSubmissionRepository.findByIdElseThrow(id);
    }

    public TextSubmission saveTextSubmission(TextSubmission textSubmission) {
        return textSubmissionRepository.save(textSubmission);
    }

    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise exercise, User user) {
        var submission = textSubmissionService.handleTextSubmission(textSubmission, exercise, user);
        textSubmissionService.hideDetails(submission, user);
        return submission;
    }
}
