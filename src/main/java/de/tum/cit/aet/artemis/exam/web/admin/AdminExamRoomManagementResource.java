package de.tum.cit.aet.artemis.exam.web.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.dto.room.ExamRoomDeletionSummaryDTO;
import de.tum.cit.aet.artemis.exam.service.ExamRoomService;
import de.tum.cit.aet.artemis.exam.web.ExamRoomManagementResource;

/**
 * REST controller for admin-only exam room management operations.
 * See the description of {@link ExamRoomManagementResource} for an explanation of the available features.
 */
@Conditional(ExamEnabled.class)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/exam/admin/")
public class AdminExamRoomManagementResource {

    private static final Logger log = LoggerFactory.getLogger(AdminExamRoomManagementResource.class);

    private final ExamRoomService examRoomService;

    public AdminExamRoomManagementResource(ExamRoomService examRoomService) {
        this.examRoomService = examRoomService;
    }

    /**
     * DELETE /exam/rooms/outdated-and-unused: Delete all outdated and unused exam rooms.
     *
     * @return a response entity with status 200 and a summary of the deletion process.
     */
    @DeleteMapping("outdated-and-unused")
    public ResponseEntity<ExamRoomDeletionSummaryDTO> deleteAllOutdatedAndUnusedExamRooms() {
        log.debug("REST request to delete all outdated and unused exam rooms");

        var examRoomDeletionSummaryDTO = examRoomService.deleteAllOutdatedAndUnusedExamRooms();
        return ResponseEntity.ok(examRoomDeletionSummaryDTO);
    }

}
