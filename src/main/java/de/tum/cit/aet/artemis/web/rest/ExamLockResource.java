package de.tum.cit.aet.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.service.exam.ExamAccessService;
import de.tum.cit.aet.artemis.service.exam.ExamService;

// only available for external version control services
@Profile("!localvc & core")
@RestController
@RequestMapping("api/")
public class ExamLockResource {

    private static final Logger log = LoggerFactory.getLogger(ExamLockResource.class);

    private final ExamService examService;

    private final ExamAccessService examAccessService;

    public ExamLockResource(ExamService examService, ExamAccessService examAccessService) {
        this.examService = examService;
        this.examAccessService = examAccessService;
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/unlock-all-repositories : Unlock all repositories of the exam (only necessary for external version control systems)
     * Locking and unlocking repositories is not supported when using the local version control system.
     * Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return the number of unlocked exercises
     */
    @PostMapping("courses/{courseId}/exams/{examId}/unlock-all-repositories")
    @EnforceAtLeastInstructor
    public ResponseEntity<Integer> unlockAllRepositories(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to unlock all repositories of exam {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Integer numOfUnlockedExercises = examService.unlockAllRepositories(examId);
        log.info("Unlocked {} programming exercises of exam {}", numOfUnlockedExercises, examId);
        return ResponseEntity.ok().body(numOfUnlockedExercises);
    }

    /**
     * POST /courses/{courseId}/exams/{examId}/lock-all-repositories : Lock all repositories of the exam (only necessary for external version control systems)
     * Locking and unlocking repositories is not supported when using the local version control system.
     * Repository access is checked in the LocalVCFetchFilter and LocalVCPushFilter.
     *
     * @param courseId the course to which the exam belongs to
     * @param examId   the id of the exam
     * @return the number of locked exercises
     */
    @PostMapping("courses/{courseId}/exams/{examId}/lock-all-repositories")
    @EnforceAtLeastInstructor
    public ResponseEntity<Integer> lockAllRepositories(@PathVariable Long courseId, @PathVariable Long examId) {
        log.info("REST request to lock all repositories of exam {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Integer numOfLockedExercises = examService.lockAllRepositories(examId);
        log.info("Locked {} programming exercises of exam {}", numOfLockedExercises, examId);
        return ResponseEntity.ok().body(numOfLockedExercises);
    }
}
