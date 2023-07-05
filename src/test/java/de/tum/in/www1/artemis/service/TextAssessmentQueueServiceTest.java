package de.tum.in.www1.artemis.service;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.repository.TextBlockRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class TextAssessmentQueueServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "textassessmentqueueservice";

    @Autowired
    private TextAssessmentQueueService textAssessmentQueueService;

    @Autowired
    private TextSubmissionService textSubmissionService;

    @Autowired
    private TextBlockRepository textBlockRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private UserUtilService userUtilService;

    private Course course;

    @BeforeEach
    void init() {
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1");
        course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
    }

}
