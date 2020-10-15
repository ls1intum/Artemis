package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.util.ResponseUtil.forbidden;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Submission;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.UserRepository;

public class SubmissionServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    CourseService courseService;

    @Autowired
    SubmissionService submissionService;

    @Autowired
    UserRepository userRepository;

    private User user;

    private Course course;

    private Exercise exercise;

    @BeforeEach
    void init() {
        List<User> users = database.addUsers(1, 0, 0);
        user = users.get(0);
        exercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        course = courseService.findAll().get(0);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testCheckSubmissionAllowance_groupCheck() {
        user.setGroups(Collections.singleton("another-group"));
        userRepository.save(user);
        Optional<ResponseEntity<Submission>> result = submissionService.checkSubmissionAllowance(exercise, null, user);
        assertThat(result.isPresent()).isTrue();
        assertThat(result.get()).isEqualTo(forbidden());
    }

}
