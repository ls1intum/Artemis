package de.tum.in.www1.artemis;

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

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.modeling.ApollonDiagram;
import de.tum.in.www1.artemis.repository.ApollonDiagramRepository;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.util.ModelFactory;

class ApollonDiagramResourceIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ApollonDiagramRepository apollonDiagramRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private UserRepository userRepo;

    private ApollonDiagram apollonDiagram;

    private Course course1;

    private Course course2;

    @BeforeEach
    void initTestCase() {
        database.addUsers(1, 1, 0, 1);
        database.createAndSaveUser("tutor2");
        database.createAndSaveUser("instructor2");

        apollonDiagram = ModelFactory.generateApollonDiagram(DiagramType.ActivityDiagram, "activityDiagram1");

        course1 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course2 = ModelFactory.generateCourse(null, ZonedDateTime.now(), ZonedDateTime.now(), new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        courseRepo.save(course1);
        courseRepo.save(course2);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
        apollonDiagram = null;
        apollonDiagramRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCreateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram response = request.postWithResponseBody("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.CREATED);
        assertThat(response.getTitle()).as("title is the same").isEqualTo(apollonDiagram.getTitle());
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testCreateApollonDiagram_BAD_REQUEST() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagram.setId(1L);
        request.post("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testCreateApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.postWithResponseBody("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testUpdateApollonDiagram_OK() throws Exception {
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram response = request.putWithResponseBody("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.OK);

        assertThat(response.getDiagramType()).as("diagram type updated").isEqualByComparingTo(DiagramType.ClassDiagram);
        assertThat(response.getTitle()).as("title updated").isEqualTo("updated title");
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testUpdateApollonDiagram_CREATED() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        request.put("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testUpdateApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram = apollonDiagramRepository.save(apollonDiagram);
        apollonDiagram.setTitle("updated title");
        apollonDiagram.setDiagramType(DiagramType.ClassDiagram);
        apollonDiagram.setCourseId(course1.getId());
        request.putWithResponseBody("/api/course/" + course1.getId() + "/apollon-diagrams", apollonDiagram, ApollonDiagram.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetDiagramsByCourse() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        List<ApollonDiagram> responseCourse1 = request.getList("/api/course/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagram.class);
        assertThat(responseCourse1).as("response is not empty").isNotEmpty();
        assertThat(responseCourse1).as("response has length 1 ").hasSize(1);

        ApollonDiagram newDiagramCourse1 = ModelFactory.generateApollonDiagram(DiagramType.ClassDiagram, "new title");
        newDiagramCourse1.setCourseId(course1.getId());
        apollonDiagramRepository.save(newDiagramCourse1);
        List<ApollonDiagram> updatedResponseCourse1 = request.getList("/api/course/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagram.class);
        assertThat(updatedResponseCourse1).as("updated response is not empty").isNotEmpty();
        assertThat(updatedResponseCourse1).as("updated response has length 2").hasSize(2);

        ApollonDiagram newDiagramCourse2 = ModelFactory.generateApollonDiagram(DiagramType.ClassDiagram, "newer title");
        newDiagramCourse2.setCourseId(course2.getId());
        apollonDiagramRepository.save(newDiagramCourse2);
        updatedResponseCourse1 = request.getList("/api/course/" + course1.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagram.class);
        assertThat(updatedResponseCourse1).as("updated response is not empty").isNotEmpty();
        assertThat(updatedResponseCourse1).as("updated response has length 2").hasSize(2);

        List<ApollonDiagram> responseCourse2 = request.getList("/api/course/" + course2.getId() + "/apollon-diagrams", HttpStatus.OK, ApollonDiagram.class);
        assertThat(responseCourse2).as("updated response is not empty").isNotEmpty();
        assertThat(responseCourse2).as("updated response has length 1").hasSize(1);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testGetDiagramsByCourse_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        apollonDiagramRepository.save(apollonDiagram);
        request.get("/api/course/" + course1.getId() + "/apollon-diagrams", HttpStatus.FORBIDDEN, List.class);
        request.get("/api/course/" + course2.getId() + "/apollon-diagrams", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        ApollonDiagram response = request.get("/api/course/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK, ApollonDiagram.class);
        assertThat(response).as("response is not null").isNotNull();
        request.get("/api/course/" + course1.getId() + "/apollon-diagrams/" + apollonDiagram.getId() + 1, HttpStatus.NOT_FOUND, ApollonDiagram.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    void testGetApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        request.get("/api/course/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN, ApollonDiagram.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testDeleteApollonDiagram() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/course/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.OK);
        assertThat(apollonDiagramRepository.findDiagramsByCourseId(course1.getId())).as("repository is empty").isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    void testDeleteApollonDiagram_AccessForbidden() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);
        request.delete("/api/course/" + course1.getId() + "/apollon-diagrams/" + savedDiagram.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void testGetDiagramTitleAsInstructor() throws Exception {
        testGetDiagramTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testGetDiagramTitleAsTeachingAssistant() throws Exception {
        testGetDiagramTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void testGetDiagramTitleAsUser() throws Exception {
        testGetDiagramTitle();
    }

    private void testGetDiagramTitle() throws Exception {
        apollonDiagram.setCourseId(course1.getId());
        ApollonDiagram savedDiagram = apollonDiagramRepository.save(apollonDiagram);

        final var title = request.get("/api/apollon-diagrams/" + savedDiagram.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(apollonDiagram.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    void testGetDiagramTitleForNonExistingDiagram() throws Exception {
        request.get("/api/apollon-diagrams/12312312412/title", HttpStatus.NOT_FOUND, String.class);
    }
}
