package de.tum.cit.aet.artemis.text.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextSubmissionRepository;
import de.tum.cit.aet.artemis.text.service.TextSubmissionService;

@Conditional(TextEnabled.class)
@Controller
@Lazy
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

    /**
     * Saves a text submission on behalf of a module that cannot reach the text service directly.
     * <p>
     * The team websocket is the only caller and never goes through the exam submission gate, so no participation is
     * passed on and the service resolves it from the authenticated user.
     *
     * The details the caller may not see are not hidden here: the saved submission's participation is a foreign key
     * rather than an entity, and the filter reads the exercise off it. The caller restores the participation it holds
     * and then calls {@link #hideDetails}.
     *
     * @param textSubmission the submission to save
     * @param exercise       the exercise it belongs to
     * @param user           the user who initiated the save
     * @return the saved submission
     */
    public TextSubmission handleTextSubmission(TextSubmission textSubmission, TextExercise exercise, User user) {
        return textSubmissionService.handleTextSubmission(textSubmission, exercise, user, null).submission();
    }

    /**
     * Hides details of a submission that should not be visible to the user.
     *
     * @param submission the submission to hide details from
     * @param user       the user for whom details should be hidden
     */
    public void hideDetails(Submission submission, User user) {
        textSubmissionService.hideDetails(submission, user);
    }
}
