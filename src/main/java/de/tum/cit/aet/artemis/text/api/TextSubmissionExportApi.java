package de.tum.cit.aet.artemis.text.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

@Controller
@Profile(PROFILE_CORE)
public class TextSubmissionExportApi extends AbstractTextApi {

    private final TextSubmissionExportService textSubmissionExportService;

    public TextSubmissionExportApi(TextSubmissionExportService textSubmissionExportService) {
        this.textSubmissionExportService = textSubmissionExportService;
    }

    public void saveSubmissionToFile(TextSubmission submission, String studentLogin, String submissionsFolderName) throws IOException {
        textSubmissionExportService.saveSubmissionToFile(submission, studentLogin, submissionsFolderName);

    }

}
