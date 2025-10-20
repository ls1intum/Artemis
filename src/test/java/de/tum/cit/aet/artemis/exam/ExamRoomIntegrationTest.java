package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomAdminOverviewDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomLayoutStrategyDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamRoomTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamRoomZipFiles;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamRoomIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examroomintegration";

    @Autowired
    private ExamRoomTestRepository examRoomRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    private static final String STUDENT_LOGIN = TEST_PREFIX + "student1";

    private static final String TUTOR_LOGIN = TEST_PREFIX + "tutor1";

    private static final String EDITOR_LOGIN = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor1";

    private static ExpectedRoom singleExpectedRoom;

    private static ExpectedRoom[] fourExpectedRooms;

    private static ExpectedRoom singleNoLayoutsExpectedRoom;

    private Course course1;

    private Exam exam1;

    private static final int NUMBER_OF_STUDENTS = 200;

    record ExpectedRoom(String roomNumber, String alternativeRoomNumber, String name, String alternativeName, String building) {

        public ExpectedRoom(String roomNumber, String alternativeRoomNumber, String name, String alternativeName, String building) {
            this.roomNumber = roomNumber;
            this.alternativeRoomNumber = roomNumber.equals(alternativeRoomNumber) ? null : alternativeRoomNumber;
            this.name = name;
            this.alternativeName = name.equals(alternativeName) ? null : alternativeName;
            this.building = building;
        }
    }

    @BeforeAll
    static void beforeAll() {
        singleExpectedRoom = new ExpectedRoom("5602.EG.001", "00.02.001", "Friedrich L. Bauer Hörsaal", "HS1", "MI");
        singleNoLayoutsExpectedRoom = new ExpectedRoom("0506.EG.601", "0601", "Theresianum", "0601", "Z6");

        var expectedRoom1 = new ExpectedRoom("0101.01.135", "N1135@0101", "Seminarraum", "N1135", "N1");
        var expectedRoom2 = new ExpectedRoom("0101.02.179", "N1179@0101", "Wilhelm-Nusselt-Hörsaal", "N1179", "N1");
        var expectedRoom3 = new ExpectedRoom("0101.Z1.090", "0101.Z1.090", "N1090", "N1090", "N1");
        var expectedRoom4 = new ExpectedRoom("5602.EG.001", "00.02.001", "Friedrich L. Bauer Hörsaal", "HS1", "MI");
        fourExpectedRooms = new ExpectedRoom[] { expectedRoom1, expectedRoom2, expectedRoom3, expectedRoom4 };
    }

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

    /* Tests for the POST /exam-rooms/upload endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testUploadExamRoomDataAsStudent() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testUploadExamRoomDataAsTutor() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testUploadExamRoomDataAsEditor() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testUploadExamRoomDataAsInstructor() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadExamRoomDataAsAdmin() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadEmptyRoomFile() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.emptyZipFile, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadSingleRoom() throws Exception {
        var uploadInformation = request.postMultipartFileOnlyWithResponseBody("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom,
                ExamRoomUploadInformationDTO.class, HttpStatus.OK);

        validateUploadOverviewAndCheckIfDbContainsRooms(uploadInformation, ExamRoomZipFiles.zipFileSingleExamRoom.getOriginalFilename(), 1, 528, singleExpectedRoom);

        validateDbStoredElementCounts(1, 528, 4);
    }

    private void validateUploadOverviewAndCheckIfDbContainsRooms(ExamRoomUploadInformationDTO uploadInformationDTO, String originalFilename, int expectedNumberOfRooms,
            int expectedNumberOfSeats, ExpectedRoom... expectedRooms) {
        // first verify the returned upload information is correct
        assertThat(uploadInformationDTO.uploadedFileName()).isEqualTo(originalFilename);
        assertThat(uploadInformationDTO.numberOfUploadedRooms()).isEqualTo(expectedNumberOfRooms);
        assertThat(uploadInformationDTO.numberOfUploadedSeats()).isEqualTo(expectedNumberOfSeats);
        assertThat(uploadInformationDTO.uploadedRoomNames()).hasSize(expectedNumberOfRooms);

        var allRoomNames = Arrays.stream(expectedRooms).map(ExpectedRoom::name).toList();
        assertThat(uploadInformationDTO.uploadedRoomNames()).containsExactlyInAnyOrderElementsOf(allRoomNames);

        // now verify the exam rooms were stored (correctly)
        Map<String, List<ExamRoom>> allExamRoomsGroupedByRoomNumber = examRoomRepository.findAllNewestExamRoomVersionsWithEagerLayoutStrategies().stream()
                .collect(Collectors.groupingBy(ExamRoom::getRoomNumber));
        assertThat(allExamRoomsGroupedByRoomNumber.keySet()).hasSize(expectedRooms.length);

        for (var expectedRoom : expectedRooms) {
            var mappedExamRooms = allExamRoomsGroupedByRoomNumber.get(expectedRoom.roomNumber());
            assertThat(mappedExamRooms).isNotNull();
            assertThat(mappedExamRooms).hasSize(1);

            var examRoom = mappedExamRooms.getFirst();
            assertThat(examRoom.getName()).isEqualTo(expectedRoom.name());
            assertThat(examRoom.getAlternativeName()).isEqualTo(expectedRoom.alternativeName());
            assertThat(examRoom.getBuilding()).isEqualTo(expectedRoom.building());
            assertThat(examRoom.getRoomNumber()).isEqualTo(expectedRoom.roomNumber());
            assertThat(examRoom.getAlternativeRoomNumber()).isEqualTo(expectedRoom.alternativeRoomNumber());
        }
    }

    private void validateDbStoredElementCounts(int expectedNumberOfRooms, int expectedNumberOfSeats, int expectedNumberOfLayoutStrategies) {
        var allExamRooms = examRoomRepository.findAllExamRoomsWithEagerLayoutStrategies();
        int totalRooms = allExamRooms.size();
        assertThat(totalRooms).isEqualTo(expectedNumberOfRooms);

        int totalSeats = allExamRooms.stream().mapToInt(examRoom -> examRoom.getSeats().size()).sum();
        assertThat(totalSeats).isEqualTo(expectedNumberOfSeats);

        int totalLayoutStrategies = allExamRooms.stream().mapToInt(examRoom -> examRoom.getLayoutStrategies().size()).sum();
        assertThat(totalLayoutStrategies).isEqualTo(expectedNumberOfLayoutStrategies);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadFourRooms() throws Exception {
        var uploadInformation = request.postMultipartFileOnlyWithResponseBody("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms,
                ExamRoomUploadInformationDTO.class, HttpStatus.OK);

        validateUploadOverviewAndCheckIfDbContainsRooms(uploadInformation, ExamRoomZipFiles.zipFileFourExamRooms.getOriginalFilename(), 4, 994, fourExpectedRooms);
        validateDbStoredElementCounts(4, 994, 15);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadFourRoomsMultipleTimes() throws Exception {
        // upload the same file multiple times, should not create duplicates
        for (int i = 0; i < 3; i++) {
            var uploadInformation = request.postMultipartFileOnlyWithResponseBody("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms,
                    ExamRoomUploadInformationDTO.class, HttpStatus.OK);

            validateUploadOverviewAndCheckIfDbContainsRooms(uploadInformation, ExamRoomZipFiles.zipFileFourExamRooms.getOriginalFilename(), 4, 994, fourExpectedRooms);
        }

        validateDbStoredElementCounts(4 * 3, 994 * 3, 15 * 3);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadSingleRoomNoLayouts() throws Exception {
        var uploadInformation = request.postMultipartFileOnlyWithResponseBody("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleRoomNoLayouts,
                ExamRoomUploadInformationDTO.class, HttpStatus.OK);

        validateUploadOverviewAndCheckIfDbContainsRooms(uploadInformation, ExamRoomZipFiles.zipFileSingleRoomNoLayouts.getOriginalFilename(), 1, 101, singleNoLayoutsExpectedRoom);

        validateDbStoredElementCounts(1, 101, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUploadIllegalExamRooms() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileIllegalExamRooms, HttpStatus.BAD_REQUEST);
    }

    /* Tests for the GET /exam-rooms/admin-overview endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testGetAdminOverviewAsStudent() throws Exception {
        request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAdminOverviewAsTutor() throws Exception {
        request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testGetAdminOverviewAsEditor() throws Exception {
        request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetAdminOverviewAsInstructor() throws Exception {
        request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.FORBIDDEN, Void.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewAsAdmin() throws Exception {
        request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, Void.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewEmpty() throws Exception {
        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);

        validateAdminOverview(adminOverview, 0, 0, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewFourRooms() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms, HttpStatus.OK);

        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);

        validateAdminOverview(adminOverview, 4, 994, 15, ExamRoomZipFiles.fourExamRoomNames);
    }

    private void validateAdminOverview(ExamRoomAdminOverviewDTO adminOverview, int expectedNumberOfStoredRooms, int expectedNumberOfStoredSeats,
            int expectedNumberOfStoredLayoutStrategies, String... expectedNewRoomNames) {
        assertThat(adminOverview.numberOfStoredExamRooms()).isEqualTo(expectedNumberOfStoredRooms);
        assertThat(adminOverview.numberOfStoredExamSeats()).isEqualTo(expectedNumberOfStoredSeats);
        assertThat(adminOverview.numberOfStoredLayoutStrategies()).isEqualTo(expectedNumberOfStoredLayoutStrategies);

        if (adminOverview.newestUniqueExamRooms() == null) {
            assertThat(expectedNewRoomNames).isEmpty();
            return;
        }

        // Here we know that we have newestUniqueExamRooms != null
        var newestRoomNames = adminOverview.newestUniqueExamRooms().stream().map(ExamRoomDTO::name).toList();
        assertThat(newestRoomNames).contains(expectedNewRoomNames);  // we want to test the subset containment
        // because `newestRoomNames` could contain older rooms we didn't upload in the latest run

        var newestUniqueExamRoomsFromDb = examRoomRepository.findAllNewestExamRoomVersionsWithEagerLayoutStrategies().stream().map(er -> new ExamRoomDTO(er.getId(),
                er.getRoomNumber(), er.getName(), er.getBuilding(), er.getSeats().size(),
                // Because of the INCLUDE.NON_EMPTY on the admin overview, we need to map to null on empty lists
                er.getLayoutStrategies().isEmpty() ? null
                        : er.getLayoutStrategies().stream().map(ls -> new ExamRoomLayoutStrategyDTO(ls.getName(), ls.getType(), ls.getCapacity())).collect(Collectors.toSet())))
                .toList();

        assertThat(adminOverview.newestUniqueExamRooms()).containsExactlyInAnyOrderElementsOf(newestUniqueExamRoomsFromDb);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewFourRoomsMultipleTimes() throws Exception {
        for (int i = 0; i < 3; i++) {
            request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms, HttpStatus.OK);
        }

        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);
        validateAdminOverview(adminOverview, 4 * 3, 994 * 3, 15 * 3, ExamRoomZipFiles.fourExamRoomNames);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewSingleRoomNoLayouts() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleRoomNoLayouts, HttpStatus.OK);

        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);
        validateAdminOverview(adminOverview, 1, 101, 0, ExamRoomZipFiles.singleExamRoomNoLayoutsName);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewSingleRoomRepeated() throws Exception {
        final int ITERATIONS = 3;
        for (int i = 0; i < ITERATIONS; i++) {
            request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoomRepeated, HttpStatus.OK);
        }

        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);
        validateAdminOverview(adminOverview, ITERATIONS, 528 * ITERATIONS, 4 * ITERATIONS, ExamRoomZipFiles.singleExamRoomName);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAdminOverviewSingleRoomIntoExistingData() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileRealisticScenario, HttpStatus.OK);
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.OK);

        var adminOverview = request.get("/api/exam/admin/exam-rooms/admin-overview", HttpStatus.OK, ExamRoomAdminOverviewDTO.class);
        validateAdminOverview(adminOverview, 59 + 1, 14_589 + 528, 212 + 4, ExamRoomZipFiles.singleExamRoomName);
    }

    /* Tests for the DELETE /exam-rooms/outdated-and-unused endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testDeleteOutdatedAndUnusedExamRoomsAsStudent() throws Exception {
        request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testDeleteOutdatedAndUnusedExamRoomsAsTutor() throws Exception {
        request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testDeleteOutdatedAndUnusedExamRoomsAsEditor() throws Exception {
        request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testDeleteOutdatedAndUnusedExamRoomsAsInstructor() throws Exception {
        request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOutdatedAndUnusedExamRoomsAsAdmin() throws Exception {
        request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", HttpStatus.OK);
    }

    private void validateDeletionSummary(ExamRoomDeletionSummaryDTO deletionSummary, int expectedNumberOfDeletedRooms) {
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isEqualTo(expectedNumberOfDeletedRooms);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOutdatedAndUnusedExamRoomsEmpty() throws Exception {
        var deletionSummary = request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", new LinkedMultiValueMap<>(), null, ExamRoomDeletionSummaryDTO.class, HttpStatus.OK);

        validateDeletionSummary(deletionSummary, 0);
        validateDbStoredElementCounts(0, 0, 0);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOutdatedAndUnusedRealisticRoomDataNothingToDelete() throws Exception {
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileRealisticScenario, HttpStatus.OK);
        assertThat(examRoomRepository.count()).isPositive();

        var deletionSummary = request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", new LinkedMultiValueMap<>(), null, ExamRoomDeletionSummaryDTO.class, HttpStatus.OK);
        validateDeletionSummary(deletionSummary, 0);
        validateDbStoredElementCounts(59, 14_589, 212);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteOutdatedAndUnusedTonsOfExamRoomData() throws Exception {
        final int ITERATIONS = 10;
        for (int i = 0; i < ITERATIONS; i++) {
            request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileRealisticScenario, HttpStatus.OK);
        }

        assertThat(examRoomRepository.count()).isPositive();

        var deletionSummary = request.delete("/api/exam/admin/exam-rooms/outdated-and-unused", new LinkedMultiValueMap<>(), null, ExamRoomDeletionSummaryDTO.class, HttpStatus.OK);
        validateDeletionSummary(deletionSummary, 59 * (ITERATIONS - 1));
        validateDbStoredElementCounts(59, 14_589, 212);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDistributeRegisteredStudentsAsStudent() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDistributeRegisteredStudentsAsTutor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDistributeRegisteredStudentsAsEditor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDistributeRegisteredStudentsAsInstructor() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDistributeRegisteredStudentsAsAdmin() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/distribute-registered-students", Set.of(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDistributeRegisteredStudentsTooFewSeats() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileSingleExamRoom, HttpStatus.OK);

        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001"));
        request.post("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students", ids, HttpStatus.BAD_REQUEST);

        Exam storedExam = examRepository.findByIdWithExamUsersElseThrow(exam.getId());
        assertThat(storedExam).isNotNull();
        assertThat(storedExam.getExamUsers()).isNotEmpty().allSatisfy(examUser -> {
            assertThat(examUser.getPlannedRoom()).isNull();
            assertThat(examUser.getPlannedSeat()).isNull();
        });
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDistributeRegisteredStudentsEnoughSeats() throws Exception {
        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course);
        examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, 200);
        request.postMultipartFileOnly("/api/exam/admin/exam-rooms/upload", ExamRoomZipFiles.zipFileFourExamRooms, HttpStatus.OK);

        var ids = examRoomRepository.findAllIdsOfNewestExamRoomVersionsByRoomNumbers(Set.of("5602.EG.001", "0101.02.179"));
        request.postWithoutResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/distribute-registered-students", ids, HttpStatus.OK);

        Exam storedExam = examRepository.findByIdWithExamUsersElseThrow(exam.getId());
        assertThat(storedExam).isNotNull();
        assertThat(storedExam.getExamUsers()).isNotEmpty().allSatisfy(examUser -> {
            assertThat(examUser.getPlannedRoom()).isNotBlank();
            assertThat(examUser.getPlannedSeat()).isNotBlank();
        });

        var usedRooms = storedExam.getExamUsers().stream().map(ExamUser::getPlannedRoom).collect(Collectors.toSet());
        assertThat(usedRooms).containsExactlyInAnyOrder("5602.EG.001", "0101.02.179");
    }

    /* Tests for the GET /api/exam/courses/{courseId}/exams/{examId}/attendance-checker-information endpoint */

    @Test
    @WithMockUser(username = STUDENT_LOGIN, roles = "USER")
    void testGetAttendanceCheckerInformationAsStudent() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.FORBIDDEN,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = TUTOR_LOGIN, roles = "TA")
    void testGetAttendanceCheckerInformationAsTutor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.FORBIDDEN,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = EDITOR_LOGIN, roles = "EDITOR")
    void testGetAttendanceCheckerInformationAsEditor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.FORBIDDEN,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetAttendanceCheckerInformationAsInstructor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testGetAttendanceCheckerInformationAsAdmin() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void testGetAttendanceCheckerInformationNoStudentsAssigned() throws Exception {
        var attendanceCheckerInformation = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/attendance-checker-information", HttpStatus.OK,
                AttendanceCheckerAppExamInformationDTO.class);

        assertThat(attendanceCheckerInformation.examId()).isEqualTo(exam1.getId());
        assertThat(attendanceCheckerInformation.examTitle()).isEqualTo(exam1.getTitle());
        assertThat(attendanceCheckerInformation.startDate()).isEqualTo(exam1.getStartDate());
        assertThat(attendanceCheckerInformation.endDate()).isEqualTo(exam1.getEndDate());
        assertThat(attendanceCheckerInformation.isTestExam()).isFalse();
        assertThat(attendanceCheckerInformation.courseId()).isEqualTo(course1.getId());
        assertThat(attendanceCheckerInformation.courseTitle()).isEqualTo(course1.getTitle());
        assertThat(attendanceCheckerInformation.examRoomsUsedInExam()).isEmpty();

    }
}
