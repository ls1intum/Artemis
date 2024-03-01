package de.tum.in.www1.artemis.aspects;

import java.io.IOException;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.user.UserUtilService;

public class EnforceRoleInCourseTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "enforceroleincourse";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    private static Course course;

    private static final String OTHER_PREFIX = "other" + TEST_PREFIX;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private static final String STUDENT_OF_OTHER_COURSE = OTHER_PREFIX + "student1";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String TUTOR_OF_OTHER_COURSE = OTHER_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String EDITOR_OF_OTHER_COURSE = OTHER_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    private static final String INSTRUCTOR_OF_OTHER_COURSE = OTHER_PREFIX + "instructor1";

    @BeforeEach
    void setup() throws IOException {
        course = courseUtilService.createCourseWithUserPrefix(TEST_PREFIX);

        // create users of course
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // create users of other course
        userUtilService.addStudent(OTHER_PREFIX + "students", STUDENT_OF_OTHER_COURSE);
        userUtilService.addTeachingAssistant(OTHER_PREFIX + "tutors", TUTOR_OF_OTHER_COURSE);
        userUtilService.addEditor(OTHER_PREFIX + "editors", EDITOR_OF_OTHER_COURSE);
        userUtilService.addInstructor(OTHER_PREFIX + "instructors", INSTRUCTOR_OF_OTHER_COURSE);
    }

    private void callEndpoint(String endpoint, HttpStatus expectedStatus) throws Exception {
        request.get("/api/test/" + endpoint + "/" + course.getId(), expectedStatus, Void.class);
    }

    private static Stream<Arguments> generateArgumentStream(HttpStatus[] expectedStatus) {
        return Stream.of(Arguments.of("testEnforceAtLeastStudentInCourseExplicit", expectedStatus[0]), Arguments.of("testEnforceAtLeastStudentInCourse", expectedStatus[1]),
                Arguments.of("testEnforceAtLeastTutorInCourseExplicit", expectedStatus[2]), Arguments.of("testEnforceAtLeastTutorInCourse", expectedStatus[3]),
                Arguments.of("testEnforceAtLeastEditorInCourseExplicit", expectedStatus[4]), Arguments.of("testEnforceAtLeastEditorInCourse", expectedStatus[5]),
                Arguments.of("testEnforceAtLeastInstructorInCourseExplicit", expectedStatus[6]), Arguments.of("testEnforceAtLeastInstructorInCourse", expectedStatus[7]),
                Arguments.of("testEnforceRoleInCourseFieldName", expectedStatus[8]), Arguments.of("testEnforceAtLeastStudentInCourseFieldName", expectedStatus[9]),
                Arguments.of("testEnforceAtLeastTutorInCourseFieldName", expectedStatus[10]), Arguments.of("testEnforceAtLeastEditorInCourseFieldName", expectedStatus[11]),
                Arguments.of("testEnforceAtLeastInstructorInCourseFieldName", expectedStatus[12]));
    }

    private static Stream<Arguments> allSameStatusProvider(HttpStatus expectedStatus) {
        return generateArgumentStream(Stream.generate(() -> expectedStatus).limit(13).toArray(HttpStatus[]::new));
    }

    private static Stream<Arguments> testAsStudentOfCourseProvider() {
        return generateArgumentStream(new HttpStatus[] { HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN,
                HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN, HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN });
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsStudentOfCourseProvider")
    @WithMockUser(username = STUDENT_OF_COURSE, roles = "USER")
    void testAsStudentOfCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsStudentOfOtherCourseProvider() {
        return allSameStatusProvider(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsStudentOfOtherCourseProvider")
    @WithMockUser(username = STUDENT_OF_OTHER_COURSE, roles = "USER")
    void testAsStudentOfOther(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsTutorOfCourseProvider() {
        return generateArgumentStream(new HttpStatus[] { HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN,
                HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN, HttpStatus.FORBIDDEN });
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsTutorOfCourseProvider")
    @WithMockUser(username = TUTOR_OF_COURSE, roles = "TA")
    void testAsTutorOfCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsTutorOfOtherCourseProvider() {
        return allSameStatusProvider(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsTutorOfOtherCourseProvider")
    @WithMockUser(username = TUTOR_OF_OTHER_COURSE, roles = "TA")
    void testAsTutorOfOtherCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsEditorOfCourseProvider() {
        return generateArgumentStream(new HttpStatus[] { HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN,
                HttpStatus.FORBIDDEN, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.OK, HttpStatus.FORBIDDEN });
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsEditorOfCourseProvider")
    @WithMockUser(username = EDITOR_OF_COURSE, roles = "EDITOR")
    void testAsEditorOfCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsEditorOfOtherCourseProvider() {
        return allSameStatusProvider(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsEditorOfOtherCourseProvider")
    @WithMockUser(username = EDITOR_OF_OTHER_COURSE, roles = "EDITOR")
    void testAsEditorOfOtherCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsInstructorOfCourseProvider() {
        return allSameStatusProvider(HttpStatus.OK);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsInstructorOfCourseProvider")
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testAsInstructorOfCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }

    private static Stream<Arguments> testAsInstructorOfOtherCourseProvider() {
        return allSameStatusProvider(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsInstructorOfOtherCourseProvider")
    @WithMockUser(username = INSTRUCTOR_OF_OTHER_COURSE, roles = "INSTRUCTOR")
    void testAsInstructorOfOtherCourse(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }
}
