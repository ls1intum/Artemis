package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ReseatInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.SeatsOfExamRoomDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamRoomDistributionService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamRoomTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamRoomZipFiles;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamRoomDistributionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "roomdistributionintegration";

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamRoomTestRepository examRoomRepository;

    @Autowired
    private ExamRoomService examRoomService;

    @Autowired
    private ExamRoomDistributionService examRoomDistributionService;

    @Autowired
    private ExamUserRepository examUserRepository;

    private static final String STUDENT_LOGIN = TEST_PREFIX + "student1";

    private static final String TUTOR_LOGIN = TEST_PREFIX + "tutor1";

    private static final String EDITOR_LOGIN = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor1";

    private static final int NUMBER_OF_STUDENTS = 200;

    private Course course1;

    private Exam exam1;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        course1 = courseUtilService.addEmptyCourse();
        exam1 = examUtilService.addExam(course1);
    }

    @AfterEach
    void tearDown() {
        examRoomRepository.deleteAll();
    }

    /* Tests for the POST /api/exam/courses/{courseId}/exams/{examId}/distribute-registered-students endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testDistributeRegisteredStudentsAsStudent() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testDistributeRegisteredStudentsAsTutor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testDistributeRegisteredStudentsAsEditor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsAsInstructor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDistributeRegisteredStudentsAsAdmin() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsTooFewSeats() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);
        request.postMultipartFileOnly("/api/exam/rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.OK);

        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001"));
        request.post("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students", ids, HttpStatus.BAD_REQUEST);

        verifyAllUsersAreNotDistributed(exam);
    }

    void verifyAllUsersAreNotDistributed(Exam exam) {
        Exam storedExam = examRepository.findByIdWithExamUsersElseThrow(exam.getId());
        assertThat(storedExam).isNotNull();
        assertThat(storedExam.getExamUsers()).isNotEmpty().allSatisfy(examUser -> {
            assertThat(examUser.getPlannedRoom()).isNull();
            assertThat(examUser.getPlannedSeat()).isNull();
        });
    }

    void verifyAllUsersAreDistributedAcrossExactly(Exam exam, String... roomNumbers) {
        Exam storedExam = examRepository.findByIdWithExamUsersElseThrow(exam.getId());
        Set<String> encounteredRoomsAndSeats = new HashSet<>();
        assertThat(storedExam).isNotNull();
        assertThat(storedExam.getExamUsers()).isNotEmpty().allSatisfy(examUser -> {
            assertThat(examUser.getPlannedRoom()).isNotBlank();
            assertThat(examUser.getPlannedSeat()).isNotBlank();
            String roomAndSeat = examUser.getPlannedRoom() + '\u0000' + examUser.getPlannedSeat();
            assertThat(encounteredRoomsAndSeats).doesNotContain(roomAndSeat);
            encounteredRoomsAndSeats.add(roomAndSeat);
        });

        var usedRooms = storedExam.getExamUsers().stream().map(ExamUser::getPlannedRoom).collect(Collectors.toSet());
        assertThat(usedRooms).containsExactlyInAnyOrder(roomNumbers);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsEnoughSeats() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);
        request.postMultipartFileOnly("/api/exam/rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms, HttpStatus.OK);

        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001", "0101.02.179"));
        request.postWithoutResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students", ids, HttpStatus.OK);

        verifyAllUsersAreDistributedAcrossExactly(exam, "5602.EG.001", "0101.02.179");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsAllowAlternativeLayouts() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);

        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileFourExamRooms);
        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001"));
        request.postWithoutResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students?useOnlyDefaultLayouts=false", ids,
                HttpStatus.OK);

        verifyAllUsersAreDistributedAcrossExactly(exam, "5602.EG.001");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsAllowAlternativeLayoutsButStillTooFewSeats() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);

        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileFourExamRooms);
        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("0101.02.179"));
        request.postWithoutResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students?useOnlyDefaultLayouts=false", ids,
                HttpStatus.BAD_REQUEST);

        verifyAllUsersAreNotDistributed(exam);
    }

    /* Tests for the GET /api/exam/courses/{courseId}/exams/{examId}/attendance-checker-information endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testGetAttendanceCheckerInformationAsStudent() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 1);

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.FORBIDDEN,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationAsTutor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.BAD_REQUEST,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testGetAttendanceCheckerInformationAsEditor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.BAD_REQUEST,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetAttendanceCheckerInformationAsInstructor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.BAD_REQUEST,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAttendanceCheckerInformationAsAdmin() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.BAD_REQUEST,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationRegisteredStudentsButNotDistributed() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 1);

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.BAD_REQUEST,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    void verifyBasicAttendanceCheckerInformation(AttendanceCheckerAppExamInformationDTO attendanceCheckerInformation) {
        assertThat(attendanceCheckerInformation.examId()).isEqualTo(exam1.getId());
        assertThat(attendanceCheckerInformation.examTitle()).isEqualTo(exam1.getTitle());
        assertThat(attendanceCheckerInformation.startDate()).isCloseTo(exam1.getStartDate(), byLessThan(1, ChronoUnit.SECONDS));
        assertThat(attendanceCheckerInformation.endDate()).isCloseTo(exam1.getEndDate(), byLessThan(1, ChronoUnit.SECONDS));
        assertThat(attendanceCheckerInformation.isTestExam()).isFalse();
        assertThat(attendanceCheckerInformation.courseId()).isEqualTo(course1.getId());
        assertThat(attendanceCheckerInformation.courseTitle()).isEqualTo(course1.getTitle());
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationRegisteredStudentsWithModernDistribution() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 10);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        List<Long> ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001")).stream().toList();
        examRoomDistributionService.distributeRegisteredStudents(exam1.getId(), ids, true, 0.0);

        var attendanceCheckerInformation = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);

        verifyBasicAttendanceCheckerInformation(attendanceCheckerInformation);
        assertThat(attendanceCheckerInformation.examRoomsUsedInExam()).hasSize(1);
        assertThat(attendanceCheckerInformation.examUsersWithExamRoomAndSeat()).hasSize(10);
    }

    void distributeStudentsLegacyWay(int numberOfStudents) {
        for (int i = 1; i <= numberOfStudents; i++) {
            String login = TEST_PREFIX + "student" + i;
            User user = userTestRepository.findOneWithExamUsersByLogin(login).orElseThrow();
            ExamUser examUser = user.getExamUsers().stream().filter(examUser1 -> examUser1.getExam().getId().equals(exam1.getId())).findAny().orElseThrow();

            examUser.setPlannedRoom("Room" + i);
            examUser.setPlannedSeat("2, " + i * 2 + 1);
            examUserRepository.save(examUser);
        }
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationRegisteredStudentsWithLegacyDistribution() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 10);

        distributeStudentsLegacyWay(10);

        var attendanceCheckerInformation = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);

        verifyBasicAttendanceCheckerInformation(attendanceCheckerInformation);
        assertThat(attendanceCheckerInformation.examRoomsUsedInExam()).isNullOrEmpty();
        assertThat(attendanceCheckerInformation.examUsersWithExamRoomAndSeat()).hasSize(10);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationRegisteredStudentsWithLegacyDistributionNotAllDistributed() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 10);

        distributeStudentsLegacyWay(5);

        var attendanceCheckerInformation = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);

        verifyBasicAttendanceCheckerInformation(attendanceCheckerInformation);
        assertThat(attendanceCheckerInformation.examRoomsUsedInExam()).isNullOrEmpty();
        assertThat(attendanceCheckerInformation.examUsersWithExamRoomAndSeat()).hasSize(5);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationWithStudentsAssignedReturnAllRegisteredRooms() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, 10);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileFourExamRooms);
        List<Long> ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("0101.01.135", "0101.02.179", "0101.Z1.090", "5602.EG.001")).stream().toList();
        examRoomDistributionService.distributeRegisteredStudents(exam1.getId(), ids, true, 0.0);

        var attendanceCheckerInformation = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);

        verifyBasicAttendanceCheckerInformation(attendanceCheckerInformation);
        assertThat(attendanceCheckerInformation.examRoomsUsedInExam()).hasSize(4);
        assertThat(attendanceCheckerInformation.examUsersWithExamRoomAndSeat()).hasSize(10);
    }

    /* Tests for the GET /api/exam/courses/{courseId}/exams/{examId}/rooms-used endpoint */

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testGetUsedRoomsAsEditorShouldBeForbidden() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/rooms-used", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetUsedRoomsAsInstructorShouldBeAllowed() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/rooms-used", HttpStatus.OK, Set.class);
    }

    private void uploadFourRoomsAndDistributeStudentsInExam1() {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, NUMBER_OF_STUDENTS);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileFourExamRooms);
        List<Long> ids = examRoomRepository.findAllNewestExamRoomVersions().stream().map(ExamRoom::getId).toList();
        examRoomDistributionService.distributeRegisteredStudents(exam1.getId(), ids, true, 0.1);
    }

    private void reloadExam1WithExamUsers() {
        exam1 = examRepository.findByIdWithExamUsersElseThrow(exam1.getId());
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetUsedRoomsOfExam() throws Exception {
        uploadFourRoomsAndDistributeStudentsInExam1();

        Set<ExamRoomForDistributionDTO> usedRooms = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/rooms-used", HttpStatus.OK,
                new TypeReference<>() {
                });
        assertThat(usedRooms.size()).isEqualTo(4);
        Set<ExamRoomForDistributionDTO> expectedRooms = examRoomRepository.findAllNewestExamRoomVersions().stream().map(ExamRoomForDistributionDTO::from)
                .collect(Collectors.toSet());
        assertThat(usedRooms).isEqualTo(expectedRooms);
    }

    /* Tests for the GET /api/exam/rooms/{examRoomId}/seats endpoint */

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testGetSeatsInRoomAsEditorShouldBeForbidden() throws Exception {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        ExamRoom room = examRoomRepository.findAllNewestExamRoomVersions().stream().findFirst().orElseThrow();

        request.get("/api/exam/rooms/" + room.getId() + "/seats", HttpStatus.FORBIDDEN, SeatsOfExamRoomDTO.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetSeatsInRoomAsInstructorShouldBeAllowed() throws Exception {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        ExamRoom room = examRoomRepository.findAllNewestExamRoomVersions().stream().findFirst().orElseThrow();

        request.get("/api/exam/rooms/" + room.getId() + "/seats", HttpStatus.OK, SeatsOfExamRoomDTO.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetSeatsInRoom() throws Exception {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileRealisticScenario);

        for (ExamRoom room : examRoomRepository.findAllNewestExamRoomVersions()) {
            SeatsOfExamRoomDTO seatsOfRoom = request.get("/api/exam/rooms/" + room.getId() + "/seats", HttpStatus.OK, SeatsOfExamRoomDTO.class);
            var expectedSeatNames = room.getSeats().stream().map(ExamSeatDTO::name).toList();

            assertThat(seatsOfRoom.seats()).hasSize(expectedSeatNames.size());
            assertThat(seatsOfRoom.seats()).isEqualTo(expectedSeatNames);
        }
    }

    /* Tests for the POST /api/exam/courses/{courseId}/exams/{examId}/reseat-student endpoint */

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testReseatStudentAsEditorShouldBeForbidden() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, NUMBER_OF_STUDENTS);
        reloadExam1WithExamUsers();
        ExamUser anyExamUser = exam1.getExamUsers().stream().findAny().orElseThrow();
        ReseatInformationDTO reseatInformation = new ReseatInformationDTO(anyExamUser.getId(), "SOME.ROOM", "SOME.SEAT");

        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reseat-student", reseatInformation, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testReseatStudentAsInstructorShouldBeAllowed() throws Exception {
        examUtilService.registerUsersForExamAndSaveExam(exam1, TEST_PREFIX, NUMBER_OF_STUDENTS);
        reloadExam1WithExamUsers();
        ExamUser anyExamUser = exam1.getExamUsers().stream().findAny().orElseThrow();
        ReseatInformationDTO reseatInformation = new ReseatInformationDTO(anyExamUser.getId(), "SOME.ROOM", "SOME.SEAT");

        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reseat-student", reseatInformation, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testReseatStudentAsInstructorFixedSeat() throws Exception {
        uploadFourRoomsAndDistributeStudentsInExam1();
        reloadExam1WithExamUsers();

        ExamUser anyExamUser = exam1.getExamUsers().stream().findAny().orElseThrow();

        ReseatInformationDTO reseatInformation = new ReseatInformationDTO(anyExamUser.getId(), "SOME.ROOM", "SOME.SEAT");
        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reseat-student", reseatInformation, HttpStatus.OK);

        anyExamUser = examUserRepository.findById(anyExamUser.getId()).orElseThrow();  // refresh exam user

        assertThat(anyExamUser.getPlannedRoom()).isEqualTo("SOME.ROOM");
        assertThat(anyExamUser.getPlannedSeat()).isEqualTo("SOME.SEAT");
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testReseatStudentAsInstructorDynamicSeat() throws Exception {
        uploadFourRoomsAndDistributeStudentsInExam1();
        reloadExam1WithExamUsers();

        ExamUser anyExamUser = exam1.getExamUsers().stream().findAny().orElseThrow();
        final String initialPlannedRoom = anyExamUser.getPlannedRoom();
        ExamRoom roomToDistributeTo = examRoomRepository.findAllNewestExamRoomVersions().stream().filter(examRoom -> !examRoom.getRoomNumber().equals(initialPlannedRoom)).findAny()
                .orElseThrow();

        ReseatInformationDTO reseatInformation = new ReseatInformationDTO(anyExamUser.getId(), roomToDistributeTo.getRoomNumber(), null);
        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/reseat-student", reseatInformation, HttpStatus.OK);

        anyExamUser = examUserRepository.findById(anyExamUser.getId()).orElseThrow();  // refresh exam user

        assertThat(anyExamUser.getPlannedRoom()).isEqualTo(roomToDistributeTo.getRoomNumber());
        assertThat(anyExamUser.getPlannedSeat()).isNotBlank();
    }
}
