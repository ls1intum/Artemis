package de.tum.cit.aet.artemis.iris;

import static de.tum.cit.aet.artemis.communication.FaqFactory.generateFaq;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.FaqState;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisRewriteTextRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.rewriting.RewritingVariant;

class PyrisRewritingTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "pyrisfaqingestiontest";

    @Autowired
    private FaqRepository faqRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private Faq faq1;

    private Course course1;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        long courseId = course1.getId();
        this.faq1 = generateFaq(course1, FaqState.ACCEPTED, "Faq 1 title", "Faq 1 content");
        faqRepository.save(faq1);
        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void callRewritingPipeline() throws Exception {
        irisRequestMockProvider.mockRunRewritingResponseAnd(dto -> {
        });
        PyrisRewriteTextRequestDTO requestDTO = new PyrisRewriteTextRequestDTO("test", RewritingVariant.FAQ);
        request.postWithoutResponseBody("/api/courses/" + course1.getId() + "/rewrite-text", requestDTO, HttpStatus.OK);

    }
}
