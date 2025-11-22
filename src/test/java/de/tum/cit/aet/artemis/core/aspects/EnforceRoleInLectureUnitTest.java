package de.tum.cit.aet.artemis.core.aspects;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;

class EnforceRoleInLectureUnitTest extends AbstractEnforceRoleInResourceTest {

    @Autowired
    private LectureUtilService lectureUtilService;

    private LectureUnit lectureUnit;

    private static final String TEST_PREFIX = "enforceroleinlectureunit";

    private static final String OTHER_PREFIX = "other" + TEST_PREFIX;

    private static final String STUDENT_OF_COURSE = TEST_PREFIX + "student1";

    private static final String STUDENT_OF_OTHER_COURSE = OTHER_PREFIX + "student1";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String TUTOR_OF_OTHER_COURSE = OTHER_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String EDITOR_OF_OTHER_COURSE = OTHER_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    private static final String INSTRUCTOR_OF_OTHER_COURSE = OTHER_PREFIX + "instructor1";

    @Override
    String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Override
    String getOtherPrefix() {
        return OTHER_PREFIX;
    }

    @Override
    void customSetup() {
        // Add a lecture unit to the course
        final var lecture = lectureUtilService.createLecture(course, ZonedDateTime.now().minusDays(1));
        lectureUnit = lectureUtilService.createTextUnit(lecture);
    }

    private void callEndpoint(String endpoint, HttpStatus expectedStatus) throws Exception {
        request.get("/api/core/test/" + endpoint + "/" + lectureUnit.getId(), expectedStatus, Void.class);
    }

    private static Stream<Arguments> generateArgumentStream(HttpStatus[] expectedStatus) {
        return Stream.of(Arguments.of("testEnforceAtLeastStudentInLectureUnitExplicit", expectedStatus[0]),
                Arguments.of("testEnforceAtLeastStudentInLectureUnit", expectedStatus[1]), Arguments.of("testEnforceAtLeastTutorInLectureUnitExplicit", expectedStatus[2]),
                Arguments.of("testEnforceAtLeastTutorInLectureUnit", expectedStatus[3]), Arguments.of("testEnforceAtLeastEditorInLectureUnitExplicit", expectedStatus[4]),
                Arguments.of("testEnforceAtLeastEditorInLectureUnit", expectedStatus[5]), Arguments.of("testEnforceAtLeastInstructorInLectureUnitExplicit", expectedStatus[6]),
                Arguments.of("testEnforceAtLeastInstructorInLectureUnit", expectedStatus[7]), Arguments.of("testEnforceRoleInLectureUnitFieldName", expectedStatus[8]),
                Arguments.of("testEnforceAtLeastStudentInLectureUnitFieldName", expectedStatus[9]),
                Arguments.of("testEnforceAtLeastTutorInLectureUnitFieldName", expectedStatus[10]),
                Arguments.of("testEnforceAtLeastEditorInLectureUnitFieldName", expectedStatus[11]),
                Arguments.of("testEnforceAtLeastInstructorInLectureUnitFieldName", expectedStatus[12]));
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

    private static Stream<Arguments> testAsAdminProvider() {
        return allSameStatusProvider(HttpStatus.OK);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("testAsAdminProvider")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAsAdmin(String endpoint, HttpStatus expectedStatus) throws Exception {
        callEndpoint(endpoint, expectedStatus);
    }
}
