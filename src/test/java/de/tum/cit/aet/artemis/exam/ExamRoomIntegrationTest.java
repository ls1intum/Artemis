package de.tum.cit.aet.artemis.exam;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamRoomTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamRoomZipFiles;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public class ExamRoomIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examroomintegration";

    @Autowired
    private ExamRoomTestRepository examRoomRepository;

    private static final String STUDENT_LOGIN = TEST_PREFIX + "student1";

    private static final String TUTOR_LOGIN = TEST_PREFIX + "tutor1";

    private static final String EDITOR_LOGIN = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_LOGIN = TEST_PREFIX + "instructor1";

    private static ExpectedRoom singleExpectedRoom;

    private static ExpectedRoom[] fourExpectedRooms;

    private static ExpectedRoom singleNoLayoutsExpectedRoom;

    // @formatter:off
    record ExpectedRoom (
        String roomNumber,
        String alternativeRoomNumber,
        String name,
        String alternativeName,
        String building
    ) {
        public ExpectedRoom(String roomNumber, String alternativeRoomNumber, String name, String alternativeName, String building) {
            this.roomNumber = roomNumber;
            this.alternativeRoomNumber = roomNumber.equals(alternativeRoomNumber) ? null : alternativeRoomNumber;
            this.name = name;
            this.alternativeName = name.equals(alternativeName) ? null : alternativeName;
            this.building = building;
        }
    }
    // @formatter:on

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
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
    }

    @AfterEach
    void tearDown() {
        examRoomRepository.deleteAll();
    }

    // Testing if authorization works as expected
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

        validateUploadOverviewAndCheckIfDbContainsRooms(uploadInformation, ExamRoomZipFiles.zipFileSingleExamRoom.getOriginalFilename(), 1, 528, 4, singleExpectedRoom);

        validateDbStoredElementCounts(1, 528, 4);
    }

    private void validateUploadOverviewAndCheckIfDbContainsRooms(ExamRoomUploadInformationDTO uploadInformationDTO, String originalFilename, int expectedNumberOfRooms,
            int expectedNumberOfSeats, int expectedNumberOfLayoutStrategies, ExpectedRoom... expectedRooms) {
        // first verify the returned upload information is correct
        assertThat(uploadInformationDTO.uploadedFileName()).isEqualTo(originalFilename);
        assertThat(uploadInformationDTO.uploadDuration()).isNotNull();
        assertThat(uploadInformationDTO.numberOfUploadedRooms()).isEqualTo(expectedNumberOfRooms);
        assertThat(uploadInformationDTO.numberOfUploadedSeats()).isEqualTo(expectedNumberOfSeats);
        assertThat(uploadInformationDTO.uploadedRoomNames()).hasSize(expectedNumberOfRooms);

        var allRoomNames = Arrays.stream(expectedRooms).map(ExpectedRoom::name).toList();
        assertThat(uploadInformationDTO.uploadedRoomNames()).containsExactlyInAnyOrderElementsOf(allRoomNames);

        // now verify the exam rooms were stored (correctly)
        Map<String, List<ExamRoom>> allExamRoomsGroupedByRoomNumber = examRoomRepository.findAllOutdatedAndUnusedExamRooms().stream()
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
}
