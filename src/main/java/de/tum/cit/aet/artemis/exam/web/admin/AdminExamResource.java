package de.tum.cit.aet.artemis.exam.web.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
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
import de.tum.cit.aet.artemis.exam.dto.ExamRoomUploadInformationDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamRoomRepository;
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

    private final ExamRoomRepository examRoomRepository;

    public AdminExamResource(ExamRepository examRepository, ExamRoomService examRoomService, ExamRoomRepository examRoomRepository) {
        this.examRepository = examRepository;
        this.examRoomService = examRoomService;
        this.examRoomRepository = examRoomRepository;
    }

    /**
     * GET /exams/upcoming: Find all current and upcoming exams.
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
     * POST /api/exam/admin/exam-rooms/upload: Upload a zip file containing room data to be parsed and added to Artemis.
     *
     * @param zipFile The zip file to be uploaded. It needs to contain the `.json` files containing the room data in
     *                    the following format:
     *                    filename : long room number
     *                    "number" : short room number
     *                    "name" : long room name
     *                    "shortname" : short room name
     *                    "building" : short enclosing building name
     *                    "rows" : list of rows
     *                    "layouts" : list of layouts
     *
     * @return A DTO containing information about this upload process
     */
    @PostMapping("exam-rooms/upload")
    public ResponseEntity<ExamRoomUploadInformationDTO> uploadRoomZip(@RequestParam("file") MultipartFile zipFile) {
        log.debug("REST request to parse rooms from a zip file: {}", zipFile.getOriginalFilename());
        if (zipFile.isEmpty()) {
            throw new BadRequestAlertException("The rooms file is empty", ENTITY_NAME, "roomsFileEmpty");
        }

        var uploadInformationDTO = examRoomService.parseAndStoreExamRoomDataFromZipFile(zipFile);
        return ResponseEntity.ok(uploadInformationDTO);
    }

}
