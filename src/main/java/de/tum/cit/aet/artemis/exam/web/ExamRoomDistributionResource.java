package de.tum.cit.aet.artemis.exam.web;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomDistributionService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;

/**
 * REST controller for managing distributions of {@link ExamUser}s to {@link ExamRoom}s in an {@link Exam}
 */
@Conditional(ExamEnabled.class)
@Lazy
@RestController
@RequestMapping("api/exam/")
public class ExamRoomDistributionResource {

    private static final Logger log = LoggerFactory.getLogger(ExamRoomDistributionResource.class);

    private static final String ENTITY_NAME = "exam";

    private final ExamAccessService examAccessService;

    private final ExamRoomService examRoomService;

    private final ExamRoomDistributionService examRoomDistributionService;

    public ExamRoomDistributionResource(ExamAccessService examAccessService, ExamRoomService examRoomService, ExamRoomDistributionService examRoomDistributionService) {
        this.examAccessService = examAccessService;
        this.examRoomService = examRoomService;
        this.examRoomDistributionService = examRoomDistributionService;
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/distribute-registered-students : Distribute all students registered to
     * an exam across a selection of rooms
     *
     * @param courseId    the id of the course
     * @param examId      the id of the exam
     * @param examRoomIds the ids of all the exam rooms we want to distribute the students to
     *
     * @return 200 (OK) if the distribution was successful
     */
    @PostMapping("courses/{courseId}/exams/{examId}/distribute-registered-students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> distributeRegisteredStudents(@PathVariable long courseId, @PathVariable long examId, @RequestBody Set<Long> examRoomIds) {
        log.debug("REST request to distribute students across rooms for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (examRoomIds == null || examRoomIds.isEmpty()) {
            throw new BadRequestAlertException("You didn't specify any room IDs", ENTITY_NAME, "noRoomIDs");
        }

        if (!examRoomService.allRoomsExistAndAreNewestVersions(examRoomIds)) {
            throw new BadRequestAlertException("You have invalid room IDs", ENTITY_NAME, "invalidRoomIDs");
        }

        examRoomDistributionService.distributeRegisteredStudents(examId, examRoomIds);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/attendance-checker-information : Gets information necessary for operating
     * the attendance checker app
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return 200 (OK) if the retrieval was successful
     */
    @GetMapping("courses/{courseId}/exams/{examId}/attendance-checker-information")
    @EnforceAtLeastInstructor
    public ResponseEntity<AttendanceCheckerAppExamInformationDTO> getAttendanceCheckerAppInformation(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get attendance checker information for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var information = examRoomDistributionService.getAttendanceCheckerAppInformation(examId);

        return ResponseEntity.ok(information);
    }
}
