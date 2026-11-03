package de.tum.cit.aet.artemis.exam.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomOverviewDTO;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;

/**
 * REST controller for managing exam rooms.
 * <p>
 * Exam rooms are generally not bound to specific courses or exams. The exam room management is essentially a very
 * simplified, globally shared repository, where instructors can view existing exam rooms while preparing the
 * distribution of an exam. The data is only reachable in the context of an exam of a course the instructor teaches.
 * <p>
 * Adding new exam rooms or versions thereof is reserved for administrators, see the admin resource for exam rooms.
 * Administrators can also remove unused and outdated exam rooms. Instructors do not get these permissions, as
 * exam rooms are shared and removing is a destructive operation. Also, removing no longer required exam rooms is not
 * something that would actually affect an instructor, but rather is an operation that can be run whenever a safe
 * cleanup is required, the need for which only an admin could determine.
 * <p>
 * Course and exam specific connections between rooms and exams are managed by {@link ExamRoomDistributionResource}.
 */
@Conditional(ExamEnabled.class)
@Lazy
@FeatureUsage(UserFeature.EXAM_ROOMS)
@RestController
@RequestMapping("api/exam/rooms/")
public class ExamRoomManagementResource {

    private static final Logger log = LoggerFactory.getLogger(ExamRoomManagementResource.class);

    private final ExamRoomService examRoomService;

    private final ExamAccessService examAccessService;

    public ExamRoomManagementResource(ExamRoomService examRoomService, ExamAccessService examAccessService) {
        this.examRoomService = examRoomService;
        this.examAccessService = examAccessService;
    }

    /**
     * GET /exam/rooms/overview: Get overview over the exam room DB status.
     *
     * @param courseId the id of the course of the exam the instructor prepares
     * @param examId   the id of the exam, which has to belong to the course
     * @return a response entity with status 200 and information about the exam room DB status.
     */
    @GetMapping("overview")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<ExamRoomOverviewDTO> getExamRoomOverview(@RequestParam long courseId, @RequestParam long examId) {
        log.debug("REST request to get exam room overview for exam : {}", examId);
        examAccessService.checkExamBelongsToCourseElseThrow(courseId, examId);

        var examRoomOverviewDTO = examRoomService.getExamRoomOverview();
        return ResponseEntity.ok(examRoomOverviewDTO);
    }
}
