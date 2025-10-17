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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;
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
     * an exam across a selection of rooms.
     * <p>
     * Leaving a certain amount of seats unassigned is usually recommended, as it allows students who accidentally went
     * to the wrong room to still have a seat and participate in the exam.
     *
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @param reserveFactor how much percent of seats should remain unassigned
     * @param examRoomIds   the ids of all the exam rooms we want to distribute the students to
     * @return 200 (OK) if the distribution was successful
     */
    @PostMapping("courses/{courseId}/exams/{examId}/distribute-registered-students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> distributeRegisteredStudents(@PathVariable long courseId, @PathVariable long examId, @RequestParam(defaultValue = "0.0") double reserveFactor,
            @RequestBody Set<Long> examRoomIds) {
        log.debug("REST request to distribute students across rooms for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (examRoomIds == null || examRoomIds.isEmpty()) {
            throw new BadRequestAlertException("You didn't specify any room IDs", ENTITY_NAME, "noRoomIDs");
        }

        if (!examRoomService.allRoomsExistAndAreNewestVersions(examRoomIds)) {
            throw new BadRequestAlertException("You have invalid room IDs", ENTITY_NAME, "invalidRoomIDs");
        }

        examRoomDistributionService.distributeRegisteredStudents(examId, examRoomIds, reserveFactor);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /rooms/distribution-data : Retrieves basic room data of all available rooms, required for the instructors to be
     * able to select the rooms for distribution
     *
     * @return Basic room data of all available rooms
     */
    @GetMapping("rooms/distribution-data")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<ExamRoomForDistributionDTO>> getRoomDataForDistribution() {
        log.debug("REST request to get room data for a distribution");

        Set<ExamRoomForDistributionDTO> roomData = examRoomDistributionService.getRoomDataForDistribution();
        return ResponseEntity.ok(roomData);
    }
}
