package de.tum.in.www1.artemis.programmingexercise;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;

public final class ProgrammingSubmissionConstants {

    public static final String TEST_COMMIT = "a6250b6f03c3ae8fa8fb8fdf6bb1dc1c4cc57bad";

    public static final String BITBUCKET_REQUEST;

    public static final String BITBUCKET_REQUEST_WITHOUT_COMMIT;

    public static final String BAMBOO_REQUEST;

    public final static String GITLAB_REQUEST;

    static {
        try {
            BITBUCKET_REQUEST = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("bitbucketRequest.json"), StandardCharsets.UTF_8);
            BITBUCKET_REQUEST_WITHOUT_COMMIT = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("bitbucketRequestWithoutCommit.json"), StandardCharsets.UTF_8);
            BAMBOO_REQUEST = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("bambooRequest.json"), StandardCharsets.UTF_8);
            GITLAB_REQUEST = IOUtils.toString(ProgrammingSubmissionConstants.class.getResource("gitlabRequest.json"), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ProgrammingSubmissionConstants() {
        // do not instantiate
    }
}
