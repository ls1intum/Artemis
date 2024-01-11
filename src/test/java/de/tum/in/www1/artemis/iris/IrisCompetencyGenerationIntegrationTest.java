package de.tum.in.www1.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;

public class IrisCompetencyGenerationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscompetencyintegration";

    private Course course;

    @Autowired
    private CourseUtilService courseUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        course = courseUtilService.createCourse(1L);
        activateIrisGlobally();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void generateCompetencies_asInstructor_shouldSucceed() throws Exception {
        final String courseDescription = "Any description";
        Competency expected = new Competency();
        expected.setTitle("title");
        expected.setDescription("description");
        expected.setTaxonomy(CompetencyTaxonomy.ANALYZE);
        var competencyMap = Map.of("title", expected.getTitle(), "description", expected.getDescription(), "taxonomy", expected.getTaxonomy());
        var responseMap = Map.of("competencies", List.of(competencyMap));

        irisRequestMockProvider.mockMessageV2Response(responseMap);

        List<Competency> competencies = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/recommendations/", courseDescription, Competency.class,
                HttpStatus.OK);
        Competency actualCompetency = competencies.get(0);

        assertThat(competencies.size()).isEqualTo(1);
        assertThat(actualCompetency).usingRecursiveComparison().comparingOnlyFields("title", "description", "taxonomy").isEqualTo(expected);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testAll_asStudent_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "TUTOR")
    void testAll_asTutor_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testAll_asEditor_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    void testAllPreAuthorize() throws Exception {
        request.post("/api/courses/" + course.getId() + "/competencies/recommendations/", "a", HttpStatus.FORBIDDEN);
    }
}
