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

class IrisCompetencyGenerationIntegrationTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "iriscompetencyintegration";

    private Course course;

    @Autowired
    private CourseUtilService courseUtilService;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        course = courseUtilService.createCourse();
        activateIrisGlobally();
        activateIrisFor(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void generateCompetencies_asEditor_shouldSucceed() throws Exception {
        final String courseDescription = "Any description";
        Competency expected = new Competency();
        expected.setTitle("title");
        expected.setDescription("description");
        expected.setTaxonomy(CompetencyTaxonomy.ANALYZE);
        var competencyMap1 = Map.of("title", expected.getTitle(), "description", expected.getDescription(), "taxonomy", expected.getTaxonomy());
        // empty or malformed competencies are ignored
        var competencyMap2 = Map.of("title", "!done");
        var competencyMap3 = Map.of("malformed", "any content");
        var responseMap = Map.of("competencies", List.of(competencyMap1, competencyMap2, competencyMap3));

        irisRequestMockProvider.mockMessageV2Response(responseMap);

        List<Competency> competencies = request.postListWithResponseBody("/api/courses/" + course.getId() + "/competencies/generate-from-description", courseDescription,
                Competency.class, HttpStatus.OK);
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TUTOR")
    void testAll_asTutor_shouldReturnForbidden() throws Exception {
        testAllPreAuthorize();
    }

    void testAllPreAuthorize() throws Exception {
        request.post("/api/courses/" + course.getId() + "/competencies/generate-from-description", "a", HttpStatus.FORBIDDEN);
    }
}
