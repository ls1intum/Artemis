package de.tum.in.www1.artemis.text;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.service.TextBlockService;
import de.tum.in.www1.artemis.service.connectors.athena.AthenaService;
import de.tum.in.www1.artemis.user.UserUtilService;

class AthenaIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "athenaintegration";

    @Value("${artemis.athena.secret}")
    private String athenaApiSecret;

    @Autowired
    private TextBlockService textBlockService;

    @Autowired
    private AthenaService athenaService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

}
