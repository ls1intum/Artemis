package de.tum.cit.aet.artemis.programming.util;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class ProgrammingSubmissionConstants {

    public static final String GITLAB_PUSH_EVENT_REQUEST;

    public static final String GITLAB_PUSH_EVENT_REQUEST_WITHOUT_COMMIT;

    public static final String GITLAB_PUSH_EVENT_REQUEST_WRONG_COMMIT_ORDER;

    static {
        try {
            GITLAB_PUSH_EVENT_REQUEST = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("gitlabPushEventRequest.json"), StandardCharsets.UTF_8);
            GITLAB_PUSH_EVENT_REQUEST_WITHOUT_COMMIT = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("gitlabPushEventRequestWithoutCommit.json"),
                    StandardCharsets.UTF_8);
            GITLAB_PUSH_EVENT_REQUEST_WRONG_COMMIT_ORDER = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("gitlabPushEventRequestWrongCommitOrder.json"),
                    StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ProgrammingSubmissionConstants() {
        // do not instantiate
    }
}
