package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exam.domain.room.LayoutStrategyType;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomLayoutStrategyDTO;
import de.tum.cit.aet.artemis.exam.test_repository.ExamRoomTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamRoomZipFiles;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamRoomServiceTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private ExamRoomService examRoomService;

    @Autowired
    private ExamRoomTestRepository examRoomRepository;

    @AfterEach
    void tearDown() {
        examRoomRepository.deleteAll();
    }

    private void testParseAndStoreZipFileAndValidateUploadOverview(MultipartFile zipFile, int expectedNumberOfRooms, int expectedNumberOfSeats, String... expectedRoomNames) {
        var uploadInformation = examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFile);

        // first verify the returned upload information is correct
        assertThat(uploadInformation.uploadedFileName()).isEqualTo(zipFile.getOriginalFilename());
        assertThat(uploadInformation.numberOfUploadedRooms()).isEqualTo(expectedNumberOfRooms);
        assertThat(uploadInformation.numberOfUploadedSeats()).isEqualTo(expectedNumberOfSeats);
        assertThat(uploadInformation.uploadedRoomNames()).hasSize(expectedNumberOfRooms);
        assertThat(uploadInformation.uploadedRoomNames()).containsExactlyInAnyOrderElementsOf(Arrays.asList(expectedRoomNames));
    }

    @Test
    void testParseAndStoreZipFileSingleExamRoom() {
        testParseAndStoreZipFileAndValidateUploadOverview(ExamRoomZipFiles.zipFileSingleExamRoom, 1, 528, ExamRoomZipFiles.singleExamRoomName);
    }

    @Test
    void testParseAndStoreZipFileSingleExamRoomRepeated() {
        // Multiple entries of the same exam room should be ignored
        testParseAndStoreZipFileAndValidateUploadOverview(ExamRoomZipFiles.zipFileSingleExamRoomRepeated, 1, 528, ExamRoomZipFiles.singleExamRoomName);
    }

    @Test
    void testParseAndStoreZipFileSingleExamWithUnrelatedFiles() {
        testParseAndStoreZipFileAndValidateUploadOverview(ExamRoomZipFiles.zipFileSingleExamRoomWithUnrelatedFiles, 1, 528, ExamRoomZipFiles.singleExamRoomName);
    }

    @Test
    void testParseAndStoreZipFileSingleRoomNoLayouts() {
        // Single exam room with no layout strategies
        testParseAndStoreZipFileAndValidateUploadOverview(ExamRoomZipFiles.zipFileSingleRoomNoLayouts, 1, 101, ExamRoomZipFiles.singleExamRoomNoLayoutsName);
    }

    @Test
    void testParseAndStoreZipFileFourExamRooms() {
        testParseAndStoreZipFileAndValidateUploadOverview(ExamRoomZipFiles.zipFileFourExamRooms, 4, 994, ExamRoomZipFiles.fourExamRoomNames);
    }

    @Test
    void testParseAndStoreZipFileIllegalExamRooms() {
        // Illegal exam rooms should throw an exception
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileIllegalExamRooms));
    }

    @Test
    void testParseAndStoreZipFileRoomsRepository() {
        var uploadInformation = examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileRealisticScenario);

        assertThat(uploadInformation.uploadedFileName()).isEqualTo(ExamRoomZipFiles.zipFileRealisticScenario.getOriginalFilename());
        assertThat(uploadInformation.numberOfUploadedRooms()).isEqualTo(59);
        assertThat(uploadInformation.numberOfUploadedSeats()).isEqualTo(14_589);
        assertThat(uploadInformation.uploadedRoomNames()).hasSize(59);
    }

    @Test
    void testGetExamRoomOverview_empty() {
        var overview = examRoomService.getExamRoomOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.numberOfStoredExamRooms()).isZero();
        assertThat(overview.numberOfStoredExamSeats()).isZero();
        assertThat(overview.numberOfStoredLayoutStrategies()).isZero();
        assertThat(overview.newestUniqueExamRooms()).isEmpty();
    }

    @Test
    void testGetExamRoomOverview_insertOnce() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        var overview = examRoomService.getExamRoomOverview();

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
                new ExamRoomLayoutStrategyDTO("default", LayoutStrategyType.RELATIVE_DISTANCE, 136),
                new ExamRoomLayoutStrategyDTO("wide", LayoutStrategyType.RELATIVE_DISTANCE, 90), new ExamRoomLayoutStrategyDTO("narrow", LayoutStrategyType.RELATIVE_DISTANCE, 257),
                new ExamRoomLayoutStrategyDTO("corona", LayoutStrategyType.FIXED_SELECTION, 68) };
        assertThat(uploadedRoomDTO.layoutStrategies()).containsExactlyInAnyOrder(expectedLayoutStrategies);
    }

    @Test
    void testGetExamRoomOverview_insertMultipleTimes() {
        for (int i = 0; i < 3; i++) {
            examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        }

        var overview = examRoomService.getExamRoomOverview();
        assertThat(overview).isNotNull();
        assertThat(overview.numberOfStoredExamRooms()).isEqualTo(3);
        assertThat(overview.numberOfStoredExamSeats()).isEqualTo(528 * 3);
        assertThat(overview.numberOfStoredLayoutStrategies()).isEqualTo(4 * 3);

        assertContainsOnlyFriedrichLBauerRoomDTO(overview.newestUniqueExamRooms());
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_empty() {
        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isZero();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_noDuplicates() {
        examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);

        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isZero();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_duplicates() {
        for (int i = 0; i < 2; i++) {
            examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileSingleExamRoom);
        }

        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isOne();
    }

    @Test
    void testDeleteAllOutdatedAndUnusedExamRooms_manyDuplicates() {
        for (int i = 0; i < 5; i++) {
            examRoomService.parseAndStoreExamRoomDataFromZipFile(ExamRoomZipFiles.zipFileFourExamRooms);
        }

        // 16 outdated rooms
        var deletionSummary = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        assertThat(deletionSummary).isNotNull();
        assertThat(deletionSummary.numberOfDeletedExamRooms()).isEqualTo(16);
    }
}
