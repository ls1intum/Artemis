package de.tum.cit.aet.artemis.exam.web;

import java.util.Set;

import jakarta.validation.Valid;

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
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastTutorInCourse;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoom;
import de.tum.cit.aet.artemis.exam.domain.room.ExamRoomExamAssignment;
import de.tum.cit.aet.artemis.exam.dto.room.AttendanceCheckerAppExamInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamDistributionCapacityDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomForDistributionDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ExamSeatDTO;
import de.tum.cit.aet.artemis.exam.dto.room.ReseatInformationDTO;
import de.tum.cit.aet.artemis.exam.dto.room.SeatsOfExamRoomDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomDistributionService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;
import de.tum.cit.aet.artemis.exam.service.ExamUserService;
import jodd.util.StringUtil;

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

    private final ExamUserRepository examUserRepository;

    private final ExamUserService examUserService;

    public ExamRoomDistributionResource(ExamAccessService examAccessService, ExamRoomService examRoomService, ExamRoomDistributionService examRoomDistributionService,
            ExamUserRepository examUserRepository, ExamUserService examUserService) {
        this.examAccessService = examAccessService;
        this.examRoomService = examRoomService;
        this.examRoomDistributionService = examRoomDistributionService;
        this.examUserRepository = examUserRepository;
        this.examUserService = examUserService;
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/distribute-registered-students : Distribute all students registered to
     * an exam across a selection of rooms.
     * <p>
     * If {@code useOnlyDefaultLayouts} is {@code false}, the rooms are picked dynamically if the default capacity of
     * all rooms combined doesn't suffice. If the default capacity doesn't suffice and this is {@code true}, then an
     * error is thrown.
     * <p>
     * Leaving a certain amount of seats unassigned is usually recommended, as it allows students who accidentally went
     * to the wrong room to still have a seat and participate in the exam.
     *
     * @param courseId              the id of the course
     * @param examId                the id of the exam
     * @param useOnlyDefaultLayouts if we want to only use 'default' layouts
     * @param reserveFactor         how much percent of seats should remain unassigned. Defaults to 0%
     * @param examRoomIds           the ids of all the exam rooms we want to distribute the students to
     * @return 200 (OK) if the distribution was successful
     */
    @PostMapping("courses/{courseId}/exams/{examId}/distribute-registered-students")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> distributeRegisteredStudents(@PathVariable long courseId, @PathVariable long examId,
            @RequestParam(defaultValue = "true") boolean useOnlyDefaultLayouts, @RequestParam(defaultValue = "0.0") double reserveFactor, @RequestBody Set<Long> examRoomIds) {
        log.debug("REST request to distribute students across rooms for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (reserveFactor < 0 || reserveFactor > 1) {
            throw new BadRequestAlertException("Reserve factor outside of allowed range [0,1]", ENTITY_NAME, "reserveFactorOutOfRange");
        }

        if (examRoomIds == null || examRoomIds.isEmpty()) {
            throw new BadRequestAlertException("You didn't specify any room IDs", ENTITY_NAME, "noRoomIDs");
        }

        if (!examRoomService.allRoomsExistAndAreNewestVersions(examRoomIds)) {
            throw new BadRequestAlertException("You have invalid room IDs", ENTITY_NAME, "invalidRoomIDs");
        }

        examRoomDistributionService.distributeRegisteredStudents(examId, examRoomIds, useOnlyDefaultLayouts, reserveFactor);

        return ResponseEntity.ok().build();
    }

    /**
     * GET /rooms/distribution-data : Retrieves basic room data of all available rooms, required for the instructors to be
     * able to select the rooms for distribution
     *
     * @return 200 (OK) Basic room data of all available rooms
     */
    @GetMapping("rooms/distribution-data")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<ExamRoomForDistributionDTO>> getRoomDataForDistribution() {
        log.debug("REST request to get room data for a distribution");

        Set<ExamRoomForDistributionDTO> roomData = examRoomDistributionService.getRoomDataForDistribution();
        return ResponseEntity.ok(roomData);
    }

    /**
     * GET /rooms/distribution-capacities : Retrieves information about the combined default and maximum capacities of
     * all selected rooms, respecting the given reserve factor
     *
     * @param reserveFactor how much percent of seats should remain unassigned. Defaults to 0%
     * @param examRoomIds   the ids of all the exam rooms we want to distribute the students to
     * @return 200 (OK) Capacity information
     */
    @GetMapping("rooms/distribution-capacities")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExamDistributionCapacityDTO> getDistributionCapacities(@RequestParam double reserveFactor, @RequestParam Set<Long> examRoomIds) {
        log.debug("REST request to get capacity data for a distribution to room ids '{}' with reserve factor {}", examRoomIds, reserveFactor);

        if (reserveFactor < 0 || reserveFactor > 1) {
            throw new BadRequestAlertException("Reserve factor outside of allowed range [0,1]", ENTITY_NAME, "reserveFactorOutOfRange");
        }

        if (examRoomIds == null || examRoomIds.isEmpty()) {
            throw new BadRequestAlertException("You didn't specify any room IDs", ENTITY_NAME, "noRoomIDs");
        }

        ExamDistributionCapacityDTO capacityInformation = examRoomDistributionService.getDistributionCapacitiesByIds(examRoomIds, reserveFactor);
        return ResponseEntity.ok(capacityInformation);
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
    @EnforceAtLeastTutorInCourse
    public ResponseEntity<AttendanceCheckerAppExamInformationDTO> getAttendanceCheckerAppInformation(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get attendance checker information for exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForTeachingAssistantElseThrow(courseId, examId);

        var information = examRoomDistributionService.getAttendanceCheckerAppInformation(examId);

        return ResponseEntity.ok(information);
    }

    /**
     * GET /courses/{courseId}/exams/{examId}/rooms-used : Gets room metadata of all rooms that are used in the given exam
     *
     * @param courseId The id of the course
     * @param examId   The id of the exam
     * @return 200 (OK) All rooms used in the exam, if the retrieval was successful
     */
    @GetMapping("courses/{courseId}/exams/{examId}/rooms-used")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Set<ExamRoomForDistributionDTO>> getRoomsUsedInExam(@PathVariable long courseId, @PathVariable long examId) {
        log.debug("REST request to get rooms used in exam : {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        var rooms = examRoomDistributionService.getRoomsUsedInExam(examId);

        return ResponseEntity.ok(rooms);
    }

    /**
     * GET /rooms/{examRoomId}/seats : Gets a list of all seats of this exam room
     *
     * @param examRoomId The id of the exam room
     * @return 200 (OK) All seats of the room, if the retrieval was successful
     */
    @GetMapping("rooms/{examRoomId}/seats")
    @EnforceAtLeastInstructor
    public ResponseEntity<SeatsOfExamRoomDTO> getSeatsOfExamRoom(@PathVariable long examRoomId) {
        log.debug("REST request to get seats of exam room : {}", examRoomId);

        var seats = examRoomDistributionService.getSeatsOfExamRoom(examRoomId);
        return ResponseEntity.ok(seats);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/reseat-student : Reseat an {@link ExamUser} to a different seat.
     * <p>
     * If no new seat is explicitly specified, then this function attempts to automatically find the next available seat.
     * Obtaining this next seat is only possible if the new room is persisted.
     * <p>
     * When this function talks about persisted rooms, it refers to those {@link ExamRoom}s that are stored in the DB
     * and connected, via {@link ExamRoomExamAssignment}, to the given exam.
     * This function does not automatically register a new room when a student is distributed to a non-persisted room.
     * <p>
     * This function automatically fills gaps originating from moving a student out of a room if the old room is persisted.
     * This function does not automatically fill gaps originating from moving a student to a room.
     * This function does not fill any gaps if the user is moved within the same room.
     * <p>
     * This function does not allow to reseat a student to a seat that is already taken by another student.
     *
     * @param courseId          The id of the course
     * @param examId            The id of the exam
     * @param reseatInformation the reseating information, containing:
     *                              <ul>
     *                              <li><strong>examUserId</strong> – the ID of the exam user</li>
     *                              <li><strong>newRoom</strong> – the {@link ExamRoom#roomNumber} of the new room</li>
     *                              <li><strong>newSeat</strong> – the {@link ExamSeatDTO#name} of the new seat;
     *                              automatically determined if omitted and {@code persistedLocation == true}</li>
     *                              <li><strong>persistedLocation</strong> – whether the new location should be stored in
     *                              the database and connected to the exam</li>
     *                              </ul>
     * @return 200 (OK) on success
     */
    @PostMapping("courses/{courseId}/exams/{examId}/reseat-student")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> reseatStudent(@PathVariable long courseId, @PathVariable long examId, @Valid @RequestBody ReseatInformationDTO reseatInformation) {
        log.debug("REST request to reseat exam user : {}, for exam : {}", reseatInformation.examUserId(), examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);

        if (StringUtil.isBlank(reseatInformation.newRoom())) {
            throw new BadRequestAlertException("Invalid room number", ENTITY_NAME, "room.invalidRoomNumber");
        }

        ExamUser examUser = examUserRepository.findWithExamById(reseatInformation.examUserId()).orElseThrow();
        examUserService.setActualRoomAndSeatTransientForExamUsers(examUser.getExam().getExamUsers());

        examRoomDistributionService.reseatStudent(examUser, reseatInformation.newRoom(), reseatInformation.newSeat(), reseatInformation.persistedLocation());
        return ResponseEntity.ok().build();
    }
}
