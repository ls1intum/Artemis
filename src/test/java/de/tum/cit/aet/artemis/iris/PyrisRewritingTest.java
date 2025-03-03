package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Profile(PROFILE_IRIS)
class PyrisRewritingTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisrewritingtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Course course1;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);

        this.course1 = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        this.exercise = exerciseUtilService.getFirstExerciseWithType(this.course1, ProgrammingExercise.class);
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void callRewritingPipeline() throws Exception {
        irisRequestMockProvider.mockRunFaqRewritingResponse(dto -> {
        });
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("test", RewritingVariant.FAQ);
        request.postWithoutResponseBody("/api/courses/" + course1.getId() + "/rewrite-text", requestDTO, HttpStatus.OK);
    }

}
