package de.tum.cit.aet.artemis.exam.util;

import java.io.IOException;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Utility class for exam room related things, such as obtaining zip files, generating exam rooms, etc.
 */
public class ExamRoomZipFiles {

    /**
     * Zip file containing no files nor folders
     */
    public static final MockMultipartFile emptyZipFile;

    /**
     * Zip file containing a single exam room '5602.EG.001' ('Friedrich L. Bauer Hörsaal') with 528 seats and 4 layout strategies
     */
    public static final MockMultipartFile zipFileSingleExamRoom;

    /**
     * Same as {@link #zipFileSingleExamRoom}, but with the same exam room repeated multiple times, in nested directories.
     */
    public static final MockMultipartFile zipFileSingleExamRoomRepeated;

    /**
     * Same as {@link #zipFileSingleExamRoom}, but with unrelated (non-JSON) files with random names and content.
     */
    public static final MockMultipartFile zipFileSingleExamRoomWithUnrelatedFiles;

    /**
     * Zip file containing a single exam room '0506.EG.601' ('Theresianum') with 101 seats and no layout strategies.
     */
    public static final MockMultipartFile zipFileSingleRoomNoLayouts;

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
    public static final MockMultipartFile zipFileFourExamRooms;

    /**
     * Zip file containing JSON files with invalid formats.
     */
    public static final MockMultipartFile zipFileIllegalExamRooms;

    /**
     * Zip file containing all exam rooms stored in tumexam at the time of writing (11th August 2025)
     * This sums up to:
     * - 59 rooms
     * - 14,589 seats
     * - 212 layout strategies
     */
    public static final MockMultipartFile zipFileRealisticScenario;

    public static final String[] singleExamRoomName = { "Friedrich L. Bauer Hörsaal" };

    public static final String[] fourExamRoomNames = { "Seminarraum", "Wilhelm-Nusselt-Hörsaal", "N1090", "Friedrich L. Bauer Hörsaal" };

    public static final String[] singleExamRoomNoLayoutsName = { "Theresianum" };

    static {
        emptyZipFile = new MockMultipartFile("file", "emptyFile.zip", "application/zip", new byte[0]);
        zipFileSingleExamRoom = generateMultipartFileFromResource("test-data/exam-room/single-room.zip");
        zipFileSingleExamRoomRepeated = generateMultipartFileFromResource("test-data/exam-room/single-room-repeated.zip");
        zipFileSingleExamRoomWithUnrelatedFiles = generateMultipartFileFromResource("test-data/exam-room/single-room-with-unrelated-files.zip");
        zipFileSingleRoomNoLayouts = generateMultipartFileFromResource("test-data/exam-room/single-room-no-layouts.zip");
        zipFileFourExamRooms = generateMultipartFileFromResource("test-data/exam-room/four-rooms.zip");
        zipFileIllegalExamRooms = generateMultipartFileFromResource("test-data/exam-room/illegal-exam-rooms.zip");
        zipFileRealisticScenario = generateMultipartFileFromResource("test-data/exam-room/rooms-master.zip");
    }

    private static MockMultipartFile generateMultipartFileFromResource(String resourcePath) {
        Resource zipFileResource = new ClassPathResource(resourcePath);
        try {
            return new MockMultipartFile("file", zipFileResource.getFilename(), "application/zip", zipFileResource.getInputStream());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
