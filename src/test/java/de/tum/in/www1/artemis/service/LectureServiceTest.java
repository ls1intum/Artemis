package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class LectureServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private LectureService lectureService;

    @Autowired
    private CourseRepository courseRepository;

    private Course course;

    private Lecture lecture;

    private User student;

    private User editor;

    private Attachment testAttachment;

    @BeforeEach
    void initTestCase() throws Exception {
        List<User> users = database.addUsers(1, 0, 1, 0);
        student = users.get(0);
        editor = users.get(1);

        List<Course> courses = database.createCoursesWithExercisesAndLecturesAndLectureUnits(false, false);
        course = courseRepository.findByIdWithLecturesAndLectureUnitsElseThrow(courses.get(0).getId());
        lecture = course.getLectures().stream().findFirst().get();

        // Add a custom attachment for filtering tests
        testAttachment = ModelFactory.generateAttachment(ZonedDateTime.now().plusDays(1));
        lecture.addAttachments(testAttachment);

        assertThat(lecture).isNotNull();
        assertThat(lecture.getLectureUnits()).isNotEmpty();
        assertThat(lecture.getAttachments()).isNotEmpty();
    }

    @AfterEach
    void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testFilterActiveAttachments_editor() {
        Set<Lecture> testLectures = lectureService.filterActiveAttachments(course.getLectures(), editor);
        Lecture testLecture = testLectures.stream().filter(aLecture -> Objects.equals(aLecture.getId(), lecture.getId())).findFirst().get();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).containsExactlyElementsOf(lecture.getAttachments());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testFilterActiveAttachments_student() {
        Set<Lecture> testLectures = lectureService.filterActiveAttachments(course.getLectures(), student);
        Lecture testLecture = testLectures.stream().filter(aLecture -> Objects.equals(aLecture.getId(), lecture.getId())).findFirst().get();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).isNotEmpty();
        assertThat(testLecture.getAttachments()).containsOnlyOnceElementsOf(lecture.getAttachments());
        // Ensure that the attachment with future release date was filtered
        assertThat(testLecture.getAttachments()).doesNotContain(testAttachment);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testPageableResults() {
        PageableSearchDTO<String> pageable = database.configureLectureSearch(lecture.getTitle());
        pageable.setSortedColumn(Lecture.LectureSearchColumn.ID.name());
        pageable.setPageSize(1);

        SearchResultPageDTO<Lecture> result1 = lectureService.getAllOnPageWithSize(pageable, editor);
        assertThat(result1.getNumberOfPages()).isEqualTo(2);
        assertThat(result1.getResultsOnPage()).doesNotContain(lecture);

        pageable.setPage(2);
        SearchResultPageDTO<Lecture> result2 = lectureService.getAllOnPageWithSize(pageable, editor);
        assertThat(result2.getResultsOnPage()).containsExactly(lecture);
    }

    private SearchResultPageDTO<Lecture> searchQueryWithUser(String query, User user) {
        PageableSearchDTO<String> pageable = database.configureLectureSearch(query);
        return lectureService.getAllOnPageWithSize(pageable, user);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetLecturesPageable() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), editor);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
        assertThat(result.getResultsOnPage()).contains(lecture);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    void testGetLecturesPageable_notFound() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser("VeryLongNameThatDoesNotExist", editor);
        assertThat(result.getNumberOfPages()).isEqualTo(0);
        assertThat(result.getResultsOnPage()).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    void testGetLecturesPageable_unauthorized() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), student);
        assertThat(result.getNumberOfPages()).isEqualTo(0);
        assertThat(result.getResultsOnPage()).isEmpty();
    }
}
