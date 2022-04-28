package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
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

public class LectureServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

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
    public void initTestCase() throws Exception {
        List<User> users = database.addUsers(1, 0, 1, 0);
        this.student = users.get(0);
        this.editor = users.get(1);

        List<Course> courses = this.database.createCoursesWithExercisesAndLecturesAndLectureUnits(false, false);
        this.course = this.courseRepository.findByIdWithLecturesAndLectureUnitsElseThrow(courses.get(0).getId());
        this.lecture = course.getLectures().stream().findFirst().get();

        // Add a custom attachment for filtering tests
        this.testAttachment = ModelFactory.generateAttachment(ZonedDateTime.now().plusDays(1));
        this.lecture.addAttachments(this.testAttachment);

        assertThat(this.lecture).isNotNull();
        assertThat(this.lecture.getLectureUnits()).isNotEmpty();
        assertThat(this.lecture.getAttachments()).isNotEmpty();
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testFilterActiveAttachments_editor() {
        Set<Lecture> testLectures = lectureService.filterActiveAttachments(this.course.getLectures(), editor);
        Lecture testLecture = testLectures.stream().filter(l -> l.getId() == this.lecture.getId()).findFirst().get();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).containsExactlyElementsOf(this.lecture.getAttachments());
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testFilterActiveAttachments_student() {
        Set<Lecture> testLectures = lectureService.filterActiveAttachments(this.course.getLectures(), student);
        Lecture testLecture = testLectures.stream().filter(l -> l.getId() == this.lecture.getId()).findFirst().get();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).isNotEmpty();
        assertThat(testLecture.getAttachments()).containsOnlyOnceElementsOf(this.lecture.getAttachments());
        // Ensure that the attachment with future release date was filtered
        assertThat(testLecture.getAttachments()).doesNotContain(this.testAttachment);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testPageableResults() {
        PageableSearchDTO<String> pageable = this.database.configureLectureSearch(lecture.getTitle());
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
        PageableSearchDTO<String> pageable = this.database.configureLectureSearch(query);
        return lectureService.getAllOnPageWithSize(pageable, user);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetLecturesPageable() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), editor);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
        assertThat(result.getResultsOnPage()).contains(lecture);
    }

    @Test
    @WithMockUser(username = "editor1", roles = "EDITOR")
    public void testGetLecturesPageable_notFound() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser("VeryLongNameThatDoesNotExist", editor);
        assertThat(result.getNumberOfPages()).isEqualTo(0);
        assertThat(result.getResultsOnPage()).isEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "STUDENT")
    public void testGetLecturesPageable_unauthorized() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), student);
        assertThat(result.getNumberOfPages()).isEqualTo(0);
        assertThat(result.getResultsOnPage()).isEmpty();
    }
}
