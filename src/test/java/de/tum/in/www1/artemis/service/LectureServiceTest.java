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

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.lecture.LectureFactory;
import de.tum.in.www1.artemis.lecture.LectureUtilService;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.PageableSearchUtilService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;

class LectureServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lservicetest";

    @Autowired
    private LectureService lectureService;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    private Course course;

    private Lecture lecture;

    private User student;

    private User editor;

    private Attachment testAttachment;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 1, 0);
        student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        editor = userUtilService.getUserByLogin(TEST_PREFIX + "editor1");

        List<Course> courses = lectureUtilService.createCoursesWithExercisesAndLecturesAndLectureUnits(TEST_PREFIX, false, false, 0);
        // always use the lecture and course with the smallest/largest ID, otherwise tests below related to search might fail (in a flaky way)
        course = courseRepository.findByIdWithLecturesAndLectureUnitsElseThrow(courses.stream().min(Comparator.comparingLong(DomainObject::getId)).orElseThrow().getId());
        lecture = course.getLectures().stream().min(Comparator.comparing(Lecture::getId)).orElseThrow();
        Lecture hiddenLecture = course.getLectures().stream().max(Comparator.comparing(Lecture::getId)).orElseThrow();

        // Set one lecture only visible in the future for filtering tests
        ZonedDateTime future = ZonedDateTime.now().plusDays(3);
        hiddenLecture.setVisibleDate(future);
        hiddenLecture.setStartDate(future.plusDays(1));
        hiddenLecture.setEndDate(future.plusWeeks(1));
        lectureRepository.save(hiddenLecture);

        // Add a custom attachment for filtering tests
        testAttachment = LectureFactory.generateAttachment(ZonedDateTime.now().plusDays(1));
        lecture.addAttachments(testAttachment);
        lectureRepository.save(lecture);

        assertThat(lecture).isNotNull();
        assertThat(lecture.getLectureUnits()).isNotEmpty();
        assertThat(lecture.getAttachments()).isNotEmpty();
        assertThat(lecture.getId()).isLessThan(hiddenLecture.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testFilterActiveAttachments_editor() {
        Set<Lecture> testLectures = lectureService.filterVisibleLecturesWithActiveAttachments(course, course.getLectures(), editor);
        Lecture testLecture = testLectures.stream().filter(aLecture -> Objects.equals(aLecture.getId(), lecture.getId())).findFirst().orElseThrow();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).containsExactlyElementsOf(lecture.getAttachments());
        // Ensure that the hidden lecture is not filtered out
        assertThat(testLectures.size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterActiveAttachments_student() {
        Set<Lecture> testLectures = lectureService.filterVisibleLecturesWithActiveAttachments(course, course.getLectures(), student);
        Lecture testLecture = testLectures.stream().filter(aLecture -> Objects.equals(aLecture.getId(), lecture.getId())).findFirst().orElseThrow();
        assertThat(testLecture).isNotNull();
        assertThat(testLecture.getAttachments()).isNotEmpty();
        assertThat(testLecture.getAttachments()).containsOnlyOnceElementsOf(lecture.getAttachments());
        // Ensure that the attachment with future release date was filtered
        assertThat(testLecture.getAttachments()).doesNotContain(testAttachment);
        // Ensure that hidden lecture is filtered out for students
        assertThat(testLectures.size()).isEqualTo(1);
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

        SearchTermPageableSearchDTO<String> pageable = pageableSearchUtilService.configureLectureSearch(lecture.getTitle());
        pageable.setSortedColumn("ID");
        pageable.setPageSize(1);

        SearchResultPageDTO<Lecture> result1 = lectureService.getAllOnPageWithSize(pageable, editor);
        assertThat(result1.getNumberOfPages()).isEqualTo(2);
        assertThat(result1.getResultsOnPage()).doesNotContain(lecture);

        pageable.setPage(2);
        SearchResultPageDTO<Lecture> result2 = lectureService.getAllOnPageWithSize(pageable, editor);
        assertThat(result2.getResultsOnPage()).containsExactly(lecture);
    }

    private SearchResultPageDTO<Lecture> searchQueryWithUser(String query, User user) {
        SearchTermPageableSearchDTO<String> pageable = pageableSearchUtilService.configureLectureSearch(query);
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
