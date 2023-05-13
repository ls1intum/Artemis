package de.tum.in.www1.artemis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class LectureServiceTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "lservicetest";

    @Autowired
    private LectureService lectureService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Course course;

    private Lecture lecture;

    private User student;

    private User editor;

    private Attachment testAttachment;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 0, 1, 0);
        student = database.getUserByLogin(TEST_PREFIX + "student1");
        editor = database.getUserByLogin(TEST_PREFIX + "editor1");

        List<Course> courses = database.createCoursesWithExercisesAndLecturesAndLectureUnits(TEST_PREFIX, false, false, 0);
        // always use the lecture and course with the smallest ID, otherwise tests below related to search might fail (in a flaky way)
        course = courseRepository.findByIdWithLecturesAndLectureUnitsElseThrow(courses.stream().min(Comparator.comparingLong(DomainObject::getId)).get().getId());
        lecture = course.getLectures().stream().min(Comparator.comparing(Lecture::getId)).get();

        // Add a custom attachment for filtering tests
        testAttachment = ModelFactory.generateAttachment(ZonedDateTime.now().plusDays(1));
        lecture.addAttachments(testAttachment);
        lectureRepository.save(lecture);

        assertThat(lecture).isNotNull();
        assertThat(lecture.getLectureUnits()).isNotEmpty();
        assertThat(lecture.getAttachments()).isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testFilterActiveAttachments_editor() {
        Set<Lecture> testLectures = lectureService.filterActiveAttachments(course.getLectures(), editor);
        Lecture testLecture = testLectures.stream().filter(aLecture -> Objects.equals(aLecture.getId(), lecture.getId())).findFirst().get();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).containsExactlyElementsOf(lecture.getAttachments());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
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
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testPageableResults() {
        // the database should contain 2 lectures (see createCoursesWithExercisesAndLecturesAndLectureUnits) both with the same title
        // to enable a proper search after title, we change it

        // Use custom lecture name that is unique to test
        course.getLectures().forEach(lecture -> lecture.setTitle(lecture.getTitle() + "testPageableResults"));
        lectureRepository.saveAll(course.getLectures());
        lecture = lectureRepository.findByIdElseThrow(lecture.getId());

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
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetLecturesPageable() {
        // Use custom lecture name that is unique to test
        course.getLectures().forEach(lecture -> lecture.setTitle(lecture.getTitle() + "testGetLecturesPageable"));
        lectureRepository.saveAll(course.getLectures());
        lecture = lectureRepository.findByIdElseThrow(lecture.getId());

        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), editor);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
        assertThat(result.getResultsOnPage()).contains(lecture);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetLecturesPageable_notFound() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser("VeryLongNameThatDoesNotExist", editor);
        assertThat(result.getNumberOfPages()).isZero();
        assertThat(result.getResultsOnPage()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetLecturesPageable_unauthorized() {
        SearchResultPageDTO<Lecture> result = searchQueryWithUser(lecture.getTitle(), student);
        assertThat(result.getNumberOfPages()).isZero();
        assertThat(result.getResultsOnPage()).isEmpty();
    }
}
