package de.tum.in.www1.exerciseapp.web.rest;

import de.tum.in.www1.exerciseapp.ExerciseApplicationApp;
import de.tum.in.www1.exerciseapp.domain.Course;
import de.tum.in.www1.exerciseapp.repository.CourseRepository;
import de.tum.in.www1.exerciseapp.service.CourseService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.hamcrest.Matchers.hasItem;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Test class for the CourseResource REST controller.
 *
 * @see CourseResource
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ExerciseApplicationApp.class)
@WebAppConfiguration
@IntegrationTest
public class CourseResourceIntTest {

    private static final String DEFAULT_TITLE = "AAAAA";
    private static final String UPDATED_TITLE = "BBBBB";
    private static final String DEFAULT_STUDENT_GROUP_NAME = "AAAAA";
    private static final String UPDATED_STUDENT_GROUP_NAME = "BBBBB";

    @Inject
    private CourseRepository courseRepository;

    @Inject
    private CourseService courseService;

    @Inject
    private MappingJackson2HttpMessageConverter jacksonMessageConverter;

    @Inject
    private PageableHandlerMethodArgumentResolver pageableArgumentResolver;

    private MockMvc restCourseMockMvc;

    private Course course;

    @PostConstruct
    public void setup() {
        MockitoAnnotations.initMocks(this);
        CourseResource courseResource = new CourseResource();
        ReflectionTestUtils.setField(courseResource, "courseService", courseService);
        this.restCourseMockMvc = MockMvcBuilders.standaloneSetup(courseResource)
            .setCustomArgumentResolvers(pageableArgumentResolver)
            .setMessageConverters(jacksonMessageConverter).build();
    }

    @Before
    public void initTest() {
        course = new Course();
        course.setTitle(DEFAULT_TITLE);
        course.setStudentGroupName(DEFAULT_STUDENT_GROUP_NAME);
    }

    @Test
    @Transactional
    public void createCourse() throws Exception {
        int databaseSizeBeforeCreate = courseRepository.findAll().size();

        // Create the Course

        restCourseMockMvc.perform(post("/api/courses")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(course)))
                .andExpect(status().isCreated());

        // Validate the Course in the database
        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(databaseSizeBeforeCreate + 1);
        Course testCourse = courses.get(courses.size() - 1);
        assertThat(testCourse.getTitle()).isEqualTo(DEFAULT_TITLE);
        assertThat(testCourse.getStudentGroupName()).isEqualTo(DEFAULT_STUDENT_GROUP_NAME);
    }

    @Test
    @Transactional
    public void getAllCourses() throws Exception {
        // Initialize the database
        courseRepository.saveAndFlush(course);

        // Get all the courses
        restCourseMockMvc.perform(get("/api/courses?sort=id,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.[*].id").value(hasItem(course.getId().intValue())))
                .andExpect(jsonPath("$.[*].title").value(hasItem(DEFAULT_TITLE.toString())))
                .andExpect(jsonPath("$.[*].studentGroupName").value(hasItem(DEFAULT_STUDENT_GROUP_NAME.toString())));
    }

    @Test
    @Transactional
    public void getCourse() throws Exception {
        // Initialize the database
        courseRepository.saveAndFlush(course);

        // Get the course
        restCourseMockMvc.perform(get("/api/courses/{id}", course.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(course.getId().intValue()))
            .andExpect(jsonPath("$.title").value(DEFAULT_TITLE.toString()))
            .andExpect(jsonPath("$.studentGroupName").value(DEFAULT_STUDENT_GROUP_NAME.toString()));
    }

    @Test
    @Transactional
    public void getNonExistingCourse() throws Exception {
        // Get the course
        restCourseMockMvc.perform(get("/api/courses/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    public void updateCourse() throws Exception {
        // Initialize the database
        courseService.save(course);

        int databaseSizeBeforeUpdate = courseRepository.findAll().size();

        // Update the course
        Course updatedCourse = new Course();
        updatedCourse.setId(course.getId());
        updatedCourse.setTitle(UPDATED_TITLE);
        updatedCourse.setStudentGroupName(UPDATED_STUDENT_GROUP_NAME);

        restCourseMockMvc.perform(put("/api/courses")
                .contentType(TestUtil.APPLICATION_JSON_UTF8)
                .content(TestUtil.convertObjectToJsonBytes(updatedCourse)))
                .andExpect(status().isOk());

        // Validate the Course in the database
        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(databaseSizeBeforeUpdate);
        Course testCourse = courses.get(courses.size() - 1);
        assertThat(testCourse.getTitle()).isEqualTo(UPDATED_TITLE);
        assertThat(testCourse.getStudentGroupName()).isEqualTo(UPDATED_STUDENT_GROUP_NAME);
    }

    @Test
    @Transactional
    public void deleteCourse() throws Exception {
        // Initialize the database
        courseService.save(course);

        int databaseSizeBeforeDelete = courseRepository.findAll().size();

        // Get the course
        restCourseMockMvc.perform(delete("/api/courses/{id}", course.getId())
                .accept(TestUtil.APPLICATION_JSON_UTF8))
                .andExpect(status().isOk());

        // Validate the database is empty
        List<Course> courses = courseRepository.findAll();
        assertThat(courses).hasSize(databaseSizeBeforeDelete - 1);
    }
}
