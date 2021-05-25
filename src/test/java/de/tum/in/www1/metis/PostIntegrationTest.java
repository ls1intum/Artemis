package de.tum.in.www1.metis;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.*;

public class PostIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private LectureRepository lectureRepo;

    @Autowired
    private AttachmentRepository attachmentRepo;

    @Autowired
    private StudentQuestionRepository studentQuestionRepository;

    @Autowired
    private StudentQuestionAnswerRepository studentQuestionAnswerRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(5, 5, 0, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createRootPost() throws Exception {
        // RootPost post = database.createCourseWithExerciseAndPosts().get(0);
        // RootPost postToSave = new RootPost();
        // postToSave.setContent("Test Student Question 1");
        // postToSave.setVisibleForStudents(true);
        // postToSave.setExerciseContext(post.getExercise());
        //
        // Post createdPost = request.postWithResponseBody("/api/courses/" + postToSave.getCourse().getId() + "/student-questions",
        // postToSave, Post.class, HttpStatus.CREATED);
        //
        // assertThat(createdPost).isNotNull();
    }
}
