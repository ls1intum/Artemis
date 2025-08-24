package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomLayoutStrategyDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamRoomTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public class ExamRoomServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "examroomservicetest";

    /**
     * Zip file containing a single exam room '5602.EG.001' ('Friedrich L. Bauer Hörsaal') with 528 seats and 4 layout strategies
     */
    private static MultipartFile zipFileSingleExamRoom;

    /**
     * Same as {@link #zipFileSingleExamRoom}, but with the same exam room repeated multiple times, in nested directories.
     */
    private static MultipartFile zipFileSingleExamRoomRepeated;

    /**
     * Same as {@link #zipFileSingleExamRoom}, but with unrelated (non-JSON) files with random names and content.
     */
    private static MultipartFile zipFileSingleExamRoomWithUnrelatedFiles;

    /**
     * Zip file containing a single exam room '0506.EG.601' ('Theresianum') with 101 seats and no layout strategies.
     */
    private static MultipartFile zipFileSingleRoomNoLayouts;

    /**
     * Zip file containing 4 exam rooms:
     * - 0101.01.135 ('Seminarraum') with 48 seats and 3 layout strategies
     * - 0101.02.179 ('Wilhelm-Nusselt-Hörsaal') with 308 seats and 4 layout strategies
     * - 0101.Z1.090 ('N1090') with 110 seats and 4 layout strategies
     * - 5602.EG.001 ('Friedrich L. Bauer Hörsaal') with 528 seats and 4 layout strategies
     * <p>
     * This sums up to:
     * - 4 rooms
     * - 994 seats
     * - 15 layout strategies
     */
    private static MultipartFile zipFileFourExamRooms;

    /**
     * Zip file containing JSON files with invalid formats.
     */
    private static MultipartFile zipFileIllegalExamRooms;

    /**
     * Zip file containing all exam rooms stored in tumexam at the time of writing (11th August 2025)
     * This sums up to:
     * - 64 rooms
     * - 16,141 seats
     * - 224 layout strategies
     */
    private static MultipartFile zipFileRoomsRepository;

    private static ExpectedRoom singleExpectedRoom;

    private static ExpectedRoom[] fourExpectedRooms;

    @Autowired
    private ExamRoomService examRoomService;

    @Autowired
    private ExamRoomTestRepository examRoomRepository;

    @BeforeAll
    static void setup() throws IOException {
        zipFileSingleExamRoom = generateMultipartFileFromResource("test-data/exam-room/single-room.zip");
        zipFileSingleExamRoomRepeated = generateMultipartFileFromResource("test-data/exam-room/single-room-repeated.zip");
        zipFileSingleExamRoomWithUnrelatedFiles = generateMultipartFileFromResource("test-data/exam-room/single-room-with-unrelated-files.zip");
        zipFileSingleRoomNoLayouts = generateMultipartFileFromResource("test-data/exam-room/single-room-no-layouts.zip");
        zipFileFourExamRooms = generateMultipartFileFromResource("test-data/exam-room/four-rooms.zip");
        zipFileIllegalExamRooms = generateMultipartFileFromResource("test-data/exam-room/illegal-exam-rooms.zip");
        zipFileRoomsRepository = generateMultipartFileFromResource("test-data/exam-room/rooms-master.zip");

        singleExpectedRoom = new ExpectedRoom("5602.EG.001", "00.02.001", "Friedrich L. Bauer Hörsaal", "HS1", "MI");

        var expectedRoom1 = new ExpectedRoom("0101.01.135", "N1135@0101", "Seminarraum", "N1135", "N1");
        var expectedRoom2 = new ExpectedRoom("0101.02.179", "N1179@0101", "Wilhelm-Nusselt-Hörsaal", "N1179", "N1");
        var expectedRoom3 = new ExpectedRoom("0101.Z1.090", "0101.Z1.090", "N1090", "N1090", "N1");
        var expectedRoom4 = new ExpectedRoom("5602.EG.001", "00.02.001", "Friedrich L. Bauer Hörsaal", "HS1", "MI");
        fourExpectedRooms = new ExpectedRoom[] { expectedRoom1, expectedRoom2, expectedRoom3, expectedRoom4 };
    }

    private static String generateRandomLowercaseText(final int length) {
        Random rand = new Random();
        return IntStream.range(0, length).unordered().parallel().map(ignored -> 'z' - rand.nextInt('z' - 'a'))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    private static MultipartFile generateMultipartFileFromResource(String resourcePath) throws IOException {
        Resource zipFileResource = new ClassPathResource(resourcePath);
        return new MockMultipartFile("file", TEST_PREFIX + zipFileResource.getFilename(), "application/zip", zipFileResource.getInputStream());
    }

    @BeforeEach
    void init() {

    }

    @AfterEach
    void tearDown() {
        examRoomRepository.deleteAll();
    }

    // @formatter:off
    record ExpectedRoom (
        String roomNumber,
        String alternativeRoomNumber,
        String name,
        String alternativeName,
        String building
    ) {
        ExpectedRoom(
            String roomNumber,
            String alternativeRoomNumber,
            String name,
            String alternativeName,
            String building
        ) {
            this.roomNumber = roomNumber;
            this.alternativeRoomNumber = roomNumber.equals(alternativeRoomNumber) ? null : alternativeRoomNumber;
            this.name = name;
            this.alternativeName = name.equals(alternativeName) ? null : alternativeName;
            this.building = building;
        }
    }
    // @formatter:on

    private void testParseAndStoreZipFile(MultipartFile zipFile, int expectedNumberOfRooms, int expectedNumberOfSeats, int expectedNumberOfLayoutStrategies,
            ExpectedRoom... expectedRooms) {
        var uploadInformation = examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFile);

        // first verify the returned upload information is correct
        assertThat(uploadInformation.uploadedFileName()).isEqualTo(zipFile.getOriginalFilename());
        assertThat(uploadInformation.uploadDuration()).isNotNull();
        assertThat(uploadInformation.numberOfUploadedRooms()).isEqualTo(expectedNumberOfRooms);
        assertThat(uploadInformation.numberOfUploadedSeats()).isEqualTo(expectedNumberOfSeats);
        assertThat(uploadInformation.uploadedRoomNames()).hasSize(expectedNumberOfRooms);

        var allRoomNames = Arrays.stream(expectedRooms).map(ExpectedRoom::name).toList();
        assertThat(uploadInformation.uploadedRoomNames()).containsExactlyInAnyOrderElementsOf(allRoomNames);

        // now verify the exam rooms were stored (correctly)
        Map<String, List<ExamRoom>> allExamRoomsGroupedByRoomNumber = examRoomRepository.findAllExamRoomsWithEagerLayoutStrategies().stream()
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

        int totalSeats = allExamRoomsGroupedByRoomNumber.values().stream().flatMap(List::stream).mapToInt(examRoom -> examRoom.getSeats().size()).sum();
        assertThat(totalSeats).isEqualTo(expectedNumberOfSeats);

        assertThat(totalSeats).isEqualTo(expectedNumberOfSeats);

        int totalLayoutStrategies = allExamRoomsGroupedByRoomNumber.values().stream().flatMap(List::stream).mapToInt(examRoom -> examRoom.getLayoutStrategies().size()).sum();
        assertThat(totalLayoutStrategies).isEqualTo(expectedNumberOfLayoutStrategies);
    }

    @Test
    void testParseAndStoreZipFileSingleExamRoom() {
        testParseAndStoreZipFile(zipFileSingleExamRoom, 1, 528, 4, singleExpectedRoom);
    }

    @Test
    void testParseAndStoreZipFileSingleExamRoomRepeated() {
        // Multiple entries of the same exam room should be ignored
        testParseAndStoreZipFile(zipFileSingleExamRoomRepeated, 1, 528, 4, singleExpectedRoom);
    }

    @Test
    void testParseAndStoreZipFileSingleExamWithUnrelatedFiles() {
        testParseAndStoreZipFile(zipFileSingleExamRoomWithUnrelatedFiles, 1, 528, 4, singleExpectedRoom);
    }

    @Test
    void testParseAndStoreZipFileSingleRoomNoLayouts() {
        // Single exam room with no layout strategies
        testParseAndStoreZipFile(zipFileSingleRoomNoLayouts, 1, 101, 0, new ExpectedRoom("0506.EG.601", "0601", "Theresianum", "0601", "Z6"));
    }

    @Test
    void testParseAndStoreZipFileFourExamRooms() {
        testParseAndStoreZipFile(zipFileFourExamRooms, 4, 994, 15, fourExpectedRooms);
    }

    @Test
    void testParseAndStoreZipFileIllegalExamRooms() {
        // Illegal exam rooms should throw an exception
        assertThatExceptionOfType(ResponseStatusException.class).isThrownBy(() -> examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileIllegalExamRooms))
                .withMessageContaining("400 BAD_REQUEST");
    }

    @Test
    void testParseAndStoreZipFileRoomsRepository() {
        var uploadInformation = examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileRoomsRepository);

        assertThat(uploadInformation.uploadedFileName()).isEqualTo(zipFileRoomsRepository.getOriginalFilename());
        assertThat(uploadInformation.uploadDuration()).isNotNull();
        assertThat(uploadInformation.numberOfUploadedRooms()).isEqualTo(64);
        assertThat(uploadInformation.numberOfUploadedSeats()).isEqualTo(16141);
        assertThat(uploadInformation.uploadedRoomNames()).hasSize(64);
    }

    @Test
    void testGetExamRoomAdminOverview_empty() {
        var overview = examRoomService.getExamRoomAdminOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.numberOfStoredExamRooms()).isZero();
        assertThat(overview.numberOfStoredExamSeats()).isZero();
        assertThat(overview.numberOfStoredLayoutStrategies()).isZero();
        assertThat(overview.newestUniqueExamRooms()).isEmpty();
    }

    @Test
    void testGetExamRoomAdminOverview_insertOnce() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);
        var overview = examRoomService.getExamRoomAdminOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.numberOfStoredExamRooms()).isOne();
        assertThat(overview.numberOfStoredExamSeats()).isEqualTo(528);
        assertThat(overview.numberOfStoredLayoutStrategies()).isEqualTo(4);

        assertContainsOnlyFriedrichLBauerRoomDTO(overview.newestUniqueExamRooms());
    }

    private static void assertContainsOnlyFriedrichLBauerRoomDTO(Set<ExamRoomDTO> newestUniqueExamRooms) {
        assertThat(newestUniqueExamRooms).hasSize(1);
        var uploadedRoomDTO = newestUniqueExamRooms.iterator().next();

        assertThat(uploadedRoomDTO.roomNumber()).isEqualTo("5602.EG.001");
        assertThat(uploadedRoomDTO.name()).isEqualTo("Friedrich L. Bauer Hörsaal");
        assertThat(uploadedRoomDTO.numberOfSeats()).isEqualTo(528);
        assertThat(uploadedRoomDTO.building()).isEqualTo("MI");

        ExamRoomLayoutStrategyDTO[] expectedLayoutStrategies = new ExamRoomLayoutStrategyDTO[] {
                new ExamRoomLayoutStrategyDTO("default", LayoutStrategyType.RELATIVE_DISTANCE, 139),
                new ExamRoomLayoutStrategyDTO("wide", LayoutStrategyType.RELATIVE_DISTANCE, 93), new ExamRoomLayoutStrategyDTO("narrow", LayoutStrategyType.RELATIVE_DISTANCE, 497),
                new ExamRoomLayoutStrategyDTO("corona", LayoutStrategyType.FIXED_SELECTION, 68) };
        assertThat(uploadedRoomDTO.layoutStrategies()).containsExactlyInAnyOrder(expectedLayoutStrategies);
    }

    @Test
    void testGetExamRoomAdminOverview_insertMultipleTimes() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);

        var overview = examRoomService.getExamRoomAdminOverview();
        assertThat(overview).isNotNull();
        assertThat(overview.numberOfStoredExamRooms()).isEqualTo(3);
        assertThat(overview.numberOfStoredExamSeats()).isEqualTo(528 * 3);
        assertThat(overview.numberOfStoredLayoutStrategies()).isEqualTo(4 * 3);

        assertContainsOnlyFriedrichLBauerRoomDTO(overview.newestUniqueExamRooms());
    }

    @Test
    void testDeleteAllExamRooms() {
        // used to check that we don't accidentally write to the DB
        examRoomService.deleteAllExamRooms();
        assertThat(examRoomRepository.findAll()).isEmpty();

        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);
        assertThat(examRoomRepository.findAll()).isNotEmpty();
        examRoomService.deleteAllExamRooms();
        assertThat(examRoomRepository.findAll()).isEmpty();

        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);
        assertThat(examRoomRepository.findAll()).isNotEmpty();
        examRoomService.deleteAllExamRooms();
        assertThat(examRoomRepository.findAll()).isEmpty();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_empty() {
        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.deleteDuration()).endsWith("ms");
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isZero();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_noDuplicates() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);

        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.deleteDuration()).endsWith("ms");
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isZero();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_duplicates() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileSingleExamRoom);

        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.deleteDuration()).endsWith("ms");
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isOne();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_manyDuplicates() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);
        examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFileFourExamRooms);

        // 16 outdated rooms
        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.deleteDuration()).endsWith("ms");
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isEqualTo(16);
    }
}
