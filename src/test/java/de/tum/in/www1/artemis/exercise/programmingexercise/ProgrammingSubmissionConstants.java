package de.tum.in.www1.artemis.exercise.programmingexercise;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class ProgrammingSubmissionConstants {

    public static final String TEST_COMMIT = "a6250b6f03c3ae8fa8fb8fdf6bb1dc1c4cc57bad";

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
