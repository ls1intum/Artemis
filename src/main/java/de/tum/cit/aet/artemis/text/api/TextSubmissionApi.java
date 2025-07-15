package de.tum.cit.aet.artemis.text.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

@ConditionalOnProperty(name = "artemis.text.enabled", havingValue = "true")
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
