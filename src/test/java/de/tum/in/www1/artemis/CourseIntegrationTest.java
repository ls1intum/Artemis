package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.util.ModelGenrator;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase
public class CourseIntegrationTest {
  @Autowired
  RequestUtilService request;

  @Autowired CourseRepository courseRepo;

  @Before
  public void resetDatabase() {
    courseRepo.deleteAll();
    assertThat(courseRepo.findAll()).as("Database got cleared before test").isEmpty();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  public void createCourseWithPermission() throws Exception {
    Course course = ModelGenrator.generateCourse(null, null, null, new HashSet<>());
    request.post("/api/courses", course, HttpStatus.CREATED);
    List<Course> repoContent = courseRepo.findAll();
    assertThat(repoContent.size()).as("Course got stored").isEqualTo(1);

    course = ModelGenrator.generateCourse(1L, null, null, new HashSet<>());
    request.post("/api/courses", course, HttpStatus.BAD_REQUEST);
    assertThat(courseRepo.findAll())
        .as("Course has not been stored")
        .contains(repoContent.toArray(new Course[0]));
  }

  @Test
  @WithMockUser(roles = "USER")
  public void createCourseWithoutPermission() throws Exception {
    Course course = ModelGenrator.generateCourse(null, null, null, new HashSet<>());
    request.post("/api/courses", course, HttpStatus.FORBIDDEN);
    assertThat(courseRepo.findAll().size()).as("Course got stored").isEqualTo(0);
  }

  @Test
  @WithMockUser(roles = "USER")
  public void getCourseWithoutPermission() throws Exception {
    request.getList("/api/courses", HttpStatus.FORBIDDEN, Course.class);
  }

  public void loadInitialCourses() {
    Course course = ModelGenrator.generateCourse(1L, null, null, new HashSet<>());
    assertThat(courseRepo.findAll()).as("course repo got initialized correctly").contains(course);
  }
}
