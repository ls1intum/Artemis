package de.tum.cit.aet.artemis.modeling.apollon;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.modeling.domain.ApollonDiagram;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.dto.ApollonDiagramDTO;
import de.tum.cit.aet.artemis.modeling.repository.ApollonDiagramRepository;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ApollonDiagramResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "repositoryintegration";

    // A representative non-empty Apollon model JSON. Non-empty matters: @JsonInclude(NON_EMPTY) on the DTO/entity omits an
    // empty jsonRepresentation, so only a non-empty value actually exercises this field's entity->DTO round trip on the wire.
    private static final String JSON_REPRESENTATION = "{\"version\":\"3.0.0\",\"type\":\"ActivityDiagram\",\"elements\":{}}";

    @Autowired
    private ApollonDiagramRepository apollonDiagramRepository;

    private ApollonDiagram apollonDiagram;

    private Course course1;

    private Course course2;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");

        apollonDiagram = ModelingExerciseFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1");

        course1 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course2 = CourseFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepository.save(course1);
        courseRepository.save(course2);
    }

    @AfterEach
    void tearDown() {
        apollonDiagram = null;
        apollonDiagramRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setJsonRepresentation(JSON_REPRESENTATION);
        ApollonDiagramDTO response = request.postWithResponseBody("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagramDTO.class,
                HttpStatus.CREATED);
        assertThat(response.id()).as("response carries the generated id").isNotNull();
        assertThat(response.title()).as("title is the same").isEqualTo(apollonDiagram.getTitle());
        assertThat(response.diagramType()).as("diagram type is the same").isEqualByComparingTo(apollonDiagram.getDiagramType());
        assertThat(response.courseId()).as("course id is the same").isEqualTo(course1.getId());
        assertThat(response.jsonRepresentation()).as("json representation is the same").isEqualTo(JSON_REPRESENTATION);

        // Reload from the database to verify exactly one row was persisted with the expected fields (no duplicate insert).
        ApollonDiagram persisted = apollonDiagramRepository.findByIdElseThrow(response.id());
        assertThat(persisted.getTitle()).isEqualTo(apollonDiagram.getTitle());
        assertThat(persisted.getDiagramType()).isEqualByComparingTo(apollonDiagram.getDiagramType());
        assertThat(persisted.getCourseId()).isEqualTo(course1.getId());
        assertThat(persisted.getJsonRepresentation()).isEqualTo(JSON_REPRESENTATION);
        assertThat(apollonDiagramRepository.findDiagramsByCourseId(course1.getId())).as("exactly one diagram persisted for the course").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testCreateApollonDiagram_BAD_REQUEST() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setId(1L);
        request.post("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testCreateApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.postWithResponseBody("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagramDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateApollonDiagram_OK() throws Exception {
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setJsonRepresentation(JSON_REPRESENTATION);
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagramDTO response = request.putWithResponseBody("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagramDTO.class,
                HttpStatus.OK);

        assertThat(response.id()).as("response keeps the same id").isEqualTo(apollonDiagram.getId());
        assertThat(response.diagramType()).as("diagram type updated").isEqualByComparingTo(DiagramType.ClassDiagram);
        assertThat(response.title()).as("title updated").isEqualTo("updated title");
        assertThat(response.jsonRepresentation()).as("json representation updated").isEqualTo(JSON_REPRESENTATION);

        // Reload from the database to verify the update was persisted in place (no duplicate row).
        ApollonDiagram persisted = apollonDiagramRepository.findByIdElseThrow(apollonDiagram.getId());
        assertThat(persisted.getTitle()).isEqualTo("updated title");
        assertThat(persisted.getDiagramType()).isEqualByComparingTo(DiagramType.ClassDiagram);
        assertThat(persisted.getJsonRepresentation()).isEqualTo(JSON_REPRESENTATION);
        assertThat(apollonDiagramRepository.findDiagramsByCourseId(course1.getId())).as("update did not create an additional row").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.post("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testUpdateApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setCourseId(course1.getId());
        request.putWithResponseBody("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagramDTO.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetDiagramsByCourse() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        List<ApollonDiagramDTO> responseCourse1 = request.getList("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagramDTO.class);
        assertThat(responseCourse1).as("response is not empty").isNotEmpty();
        assertThat(responseCourse1).as("response has length 1 ").hasSize(1);

        ApollonDiagram newDiagramCourse1 = ModelingExerciseFactory.generateApollonDiagram(DiagramType.ClassDiagram, "new title");
        newDiagramCourse1.setCourseId(course1.getId());
        apollonDiagramRepository.save(newDiagramCourse1);
        List<ApollonDiagramDTO> updatedResponseCourse1 = request.getList("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagramDTO.class);
        assertThat(updatedResponseCourse1).as("updated response is not empty").isNotEmpty();
        assertThat(updatedResponseCourse1).as("updated response has length 2").hasSize(2);

        ApollonDiagram newDiagramCourse2 = ModelingExerciseFactory.generateApollonDiagram(DiagramType.ClassDiagram, "newer title");
        newDiagramCourse2.setCourseId(course2.getId());
        apollonDiagramRepository.save(newDiagramCourse2);
        updatedResponseCourse1 = request.getList("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagramDTO.class);
        assertThat(updatedResponseCourse1).as("updated response is not empty").isNotEmpty();
        assertThat(updatedResponseCourse1).as("updated response has length 2").hasSize(2);

        List<ApollonDiagramDTO> responseCourse2 = request.getList("/api/modeling/courses/" + course2.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagramDTO.class);
        assertThat(responseCourse2).as("updated response is not empty").isNotEmpty();
        assertThat(responseCourse2).as("updated response has length 1").hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testGetDiagramsByCourse_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        request.get("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams", HttpStatus.FORBIDDEN, List.class);
        request.get("/api/modeling/courses/" + course2.getId() + "/apollon-diagrams", HttpStatus.FORBIDDEN, List.class);
    }

    /**
     * Hibernate gotcha guard for the list endpoint: the entity-&gt;DTO mapping must not issue per-row (N+1) queries. The
     * number of database queries is measured for one diagram and for three diagrams; it must be identical (the diagrams are
     * fetched by a single query and {@link de.tum.cit.aet.artemis.modeling.dto.ApollonDiagramDTO#of} only reads scalar
     * columns). This also guards against a future regression where a lazy association is added to {@code ApollonDiagram}
     * and the DTO factory triggers a fetch per row.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetDiagramsByCourse_dtoMappingIsConstantQueryCount() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setJsonRepresentation(JSON_REPRESENTATION);
        apollonDiagramRepository.save(apollonDiagram);

        String url = "/api/modeling/courses/" + course1.getId() + "/apollon-diagrams";
        // Warm up request-independent caches (user groups, course authorization, feature toggles) so the comparison below
        // reflects only per-row query growth and not first-call cold-cache effects.
        request.getList(url, HttpStatus.OK, ApollonDiagramDTO.class);

        queryInterceptor.startQueryCount();
        List<ApollonDiagramDTO> single = request.getList(url, HttpStatus.OK, ApollonDiagramDTO.class);
        long queriesForOneDiagram = queryInterceptor.getQueryCount();
        assertThat(single).hasSize(1);

        for (String title : List.of("second diagram", "third diagram")) {
            ApollonDiagram extra = ModelingExerciseFactory.generateApollonDiagram(DiagramType.ClassDiagram, title);
            extra.setCourseId(course1.getId());
            extra.setJsonRepresentation(JSON_REPRESENTATION);
            apollonDiagramRepository.save(extra);
        }

        queryInterceptor.startQueryCount();
        List<ApollonDiagramDTO> many = request.getList(url, HttpStatus.OK, ApollonDiagramDTO.class);
        long queriesForThreeDiagrams = queryInterceptor.getQueryCount();
        assertThat(many).hasSize(3);
        assertThat(queriesForThreeDiagrams).as("entity->DTO list mapping must not trigger per-row (N+1) queries").isEqualTo(queriesForOneDiagram);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setJsonRepresentation(JSON_REPRESENTATION);
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        ApollonDiagramDTO response = request.get("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK, ApollonDiagramDTO.class);
        assertThat(response).as("response is not null").isNotNull();
        assertThat(response.id()).as("response carries the requested id").isEqualTo(savedDiagram.getId());
        assertThat(response.title()).as("response carries the title").isEqualTo(savedDiagram.getTitle());
        assertThat(response.jsonRepresentation()).as("response carries the json representation").isEqualTo(JSON_REPRESENTATION);
        request.get("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams/" + apollonDiagram.getId() + 1, HttpStatus.NOT_FOUND, ApollonDiagramDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void testGetApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        request.get("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN, ApollonDiagramDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK);
        assertThat(apollonDiagramRepository.findDiagramsByCourseId(course1.getId())).as("repository is empty").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testDeleteApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/modeling/courses/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetDiagramTitleAsInstructor() throws Exception {
        testGetDiagramTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetDiagramTitleAsTeachingAssistant() throws Exception {
        testGetDiagramTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetDiagramTitleAsUser() throws Exception {
        testGetDiagramTitle();
    }

    private void testGetDiagramTitle() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        final var title = request.get("/api/modeling/apollon-diagrams/" + savedDiagram.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(apollonDiagram.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetDiagramTitleForNonExistingDiagram() throws Exception {
        request.get("/api/modeling/apollon-diagrams/12312312412/title", HttpStatus.NOT_FOUND, String.class);
    }
}
