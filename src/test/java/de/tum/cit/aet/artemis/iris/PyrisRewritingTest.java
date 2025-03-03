package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.IrisConsistencyCheckService;
import de.tum.cit.aet.artemis.iris.service.IrisRewritingService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

class PyrisRewritingTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisrewritingtest";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @MockitoSpyBean
    private IrisRewritingService irisRewritingService;

    @MockitoSpyBean
    private IrisConsistencyCheckService irisConsistencyCheckService;

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
        verify(irisRewritingService).executeRewritingPipeline(any(), course1, RewritingVariant.FAQ, "test");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void callRewritingPipelineAsStudentShouldThrowForbidden() throws Exception {
        irisRequestMockProvider.mockRunFaqRewritingResponse(dto -> {
        });
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("", RewritingVariant.FAQ);
        request.postWithoutResponseBody("/api/courses/" + course1.getId() + "/rewrite-text", requestDTO, HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void callConsistencyCheckPipeline() throws Exception {
        irisRequestMockProvider.mockRunConcistencyCheckResponseAnd(dto -> {
        });
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + this.exercise.getId(), null, HttpStatus.OK);
        verify(irisConsistencyCheckService).executeConsistencyCheckPipeline(any(), this.exercise);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void callConsistencyCheckPipelineShouldThrowForbidden() throws Exception {
        irisRequestMockProvider.mockRunConcistencyCheckResponseAnd(dto -> {
        });
        request.postWithoutResponseBody("/api/iris/consistency-check/exercises/" + this.exercise.getId(), null, HttpStatus.FORBIDDEN);

    }

}
