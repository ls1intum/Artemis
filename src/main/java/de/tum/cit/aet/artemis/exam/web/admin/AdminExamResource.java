package de.tum.cit.aet.artemis.exam.web.admin;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomAdminOverviewDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;

/**
 * REST controller for administrating Exam.
 */
@Conditional(ExamEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/exam/admin/")
public class AdminExamResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExamResource.class);

    private static final String ENTITY_NAME = "examRoom";

    private final ExamRepository examRepository;

    private final ExamRoomService examRoomService;

    private final MultipartProperties multipartProperties;

    public AdminExamResource(ExamRepository examRepository, ExamRoomService examRoomService, MultipartProperties multipartProperties) {
        this.examRepository = examRepository;
        this.examRoomService = examRoomService;
        this.multipartProperties = multipartProperties;
    }

    /**
     * GET /courses/upcoming-exams: Find all current and upcoming exams.
     *
     * @return the ResponseEntity with status 200 (OK) and a list of exams.
     */
    @GetMapping("courses/upcoming-exams")
    public ResponseEntity<List<Exam>> getCurrentAndUpcomingExams() {
        log.debug("REST request to get all upcoming exams");

        List<Exam> upcomingExams = examRepository.findAllCurrentAndUpcomingExams();
        return ResponseEntity.ok(upcomingExams);
    }

    /**
     * POST /exam-rooms/upload: Upload a zip file containing room data to be parsed and added to Artemis.
     *
     * @param zipFile The zip file to be uploaded. It needs to contain the `.json` files containing the room data in
     *                    the following format:
     *                    filename : room number
     *                    "number" : alternative room number
     *                    "name" : room name
     *                    "shortname" : alternative room name
     *                    "building" : short enclosing building name
     *                    "rows" : list of rows
     *                    "layouts" : list of layouts
     *
     * @return a response entity with status 200 and information about the upload process.
     */
    @PostMapping(value = "exam-rooms/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ExamRoomUploadInformationDTO> uploadRoomZip(@RequestParam("file") MultipartFile zipFile) {
        log.debug("REST request to parse rooms from a zip file: {}", zipFile.getOriginalFilename());
        if (zipFile.isEmpty()) {
            throw new BadRequestAlertException("The rooms file is empty", ENTITY_NAME, "room.fileEmpty");
        }

        final DataSize maxSize = multipartProperties.getMaxFileSize();
        final long maxBytes = maxSize.toBytes();
        if (maxBytes > 0 && zipFile.getSize() > maxBytes) {
            throw new BadRequestAlertException("The rooms file exceeds the %s limit".formatted(maxSize.toString()), ENTITY_NAME, "room.fileTooLarge",
                    Map.of("maxSize", maxSize.toString()));
        }

        var uploadInformationDTO = examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFile);
        return ResponseEntity.ok(uploadInformationDTO);
    }

    /**
     * GET /exam-rooms/admin-overview: Get overview over the exam room DB status.
     *
     * @return a response entity with status 200 and information about the exam room DB status.
     */
    @GetMapping("exam-rooms/admin-overview")
    public ResponseEntity<ExamRoomAdminOverviewDTO> getExamRoomAdminOverview() {
        log.info("REST request to get exam room admin overview");

        var examRoomAdminOverviewDTO = examRoomService.getExamRoomAdminOverview();
        return ResponseEntity.ok(examRoomAdminOverviewDTO);
    }

    /**
     * DELETE /exam-rooms/outdated-and-unused: Delete all outdated and unused exam rooms.
     *
     * @return a response entity with status 200 and a summary of the deletion process.
     */
    @DeleteMapping("exam-rooms/outdated-and-unused")
    public ResponseEntity<ExamRoomDeletionSummaryDTO> deleteAllOutdatedAndUnusedExamRooms() {
        log.debug("REST request to delete all outdated and unused exam rooms");

        var examRoomDeletionSummaryDTO = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        return ResponseEntity.ok(examRoomDeletionSummaryDTO);
    }

}
