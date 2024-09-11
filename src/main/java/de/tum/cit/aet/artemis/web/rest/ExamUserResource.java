package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamAccessService;
import de.tum.cit.aet.artemis.exam.service.ExamUserService;
import de.tum.cit.aet.artemis.service.FilePathService;
import de.tum.cit.aet.artemis.service.FileService;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUserAttendanceCheckDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUserDTO;
import de.tum.cit.aet.artemis.web.rest.dto.ExamUsersNotFoundDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;

/**
 * REST controller for managing ExamUser.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class ExamUserResource {

    private static final Logger log = LoggerFactory.getLogger(ExamUserResource.class);

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final FileService fileService;

    private final ExamAccessService examAccessService;

    private final ExamUserService examUserService;

    public ExamUserResource(ExamUserService examUserService, UserRepository userRepository, FileService fileService, ExamAccessService examAccessService,
            ExamUserRepository examUserRepository) {
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.examUserRepository = examUserRepository;
        this.examAccessService = examAccessService;
        this.examUserService = examUserService;
    }

    /**
     * POST /courses/:courseId/exams/:examId/exam-users : Update the exam user with the given DTO info
     *
     * @param examUserDTO   the dto containing exam user info
     * @param signatureFile the signature of the student
     * @param courseId      the id of the course
     * @param examId        the id of the exam
     * @return saved examUser ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExamUser> updateExamUser(@RequestPart ExamUserDTO examUserDTO, @RequestPart(value = "file", required = false) MultipartFile signatureFile,
            @PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to update {} as exam user to exam : {}", examUserDTO.login(), examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(examUserDTO.login())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + examUserDTO.login() + "\" does not exist"));

        ExamUser examUser = examUserRepository.findByExamIdAndUserId(examId, student.getId())
                .orElseThrow(() -> new EntityNotFoundException("Exam user with login: \"" + examUserDTO.login() + "\" does not exist"));

        if (signatureFile != null) {
            String oldPathString = examUser.getSigningImagePath();
            Path basePath = FilePathService.getExamUserSignatureFilePath();
            Path savePath = fileService.saveFile(signatureFile, basePath, false);
            examUser.setSigningImagePath(FilePathService.publicPathForActualPathOrThrow(savePath, examUser.getId()).toString());

            if (oldPathString != null) {
                // Only delete old file if saving the new one succeeded
                Path oldPath = FilePathService.actualPathForPublicPath(URI.create(oldPathString));
                // Don't throw an exception if the file does not exist as then it's already deleted for some reason
                fileService.schedulePathForDeletion(oldPath, 0);
            }
        }

        examUser.setDidCheckImage(examUserDTO.didCheckImage());
        examUser.setDidCheckLogin(examUserDTO.didCheckLogin());
        examUser.setDidCheckName(examUserDTO.didCheckName());
        examUser.setDidCheckRegistrationNumber(examUserDTO.didCheckRegistrationNumber());
        examUser.setActualSeat(examUserDTO.seat());
        examUser.setActualRoom(examUserDTO.room());
        examUser = examUserRepository.save(examUser);

        examUser.getUser().setVisibleRegistrationNumber(examUser.getUser().getRegistrationNumber());

        return ResponseEntity.ok().body(examUser);
    }

    /**
     * POST courses/{courseId}/exams/{examId}/exam-users-save-images : save exam user images
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @param file     the pdf file
     * @return list of not found student matriculation numbers ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users-save-images")
    @EnforceAtLeastInstructor
    public ResponseEntity<ExamUsersNotFoundDTO> saveUsersImages(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam("file") MultipartFile file) {
        log.debug("REST request to parse pdf : {}", file.getOriginalFilename());
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        return ResponseEntity.ok().body(examUserService.saveImages(examId, file));
    }

    /**
     * GET courses/{courseId}/exams/{examId}/verify-exam-users : Retrieves a list of students who started the exam but did not sign
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return list of students who did not sign ResponseEntity with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/exams/{examId}/verify-exam-users")
    @EnforceAtLeastInstructor
    public ResponseEntity<Set<ExamUserAttendanceCheckDTO>> getAllWhoDidNotSign(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to get all students who did not sign for exam with id: {}", examId);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        return ResponseEntity.ok().body(examUserRepository.findAllExamUsersWhoDidNotSign(examId));
    }

    /**
     * GET courses/{courseId}/exams/{examId}/attendance : Verifies attendance check status of the current student
     *
     * @param courseId the id of the course
     * @param examId   the id of the exam
     * @return boolean indicating if attendance was checked ResponseEntity with status 200 (OK)
     */
    @GetMapping("courses/{courseId}/exams/{examId}/attendance")
    @EnforceAtLeastStudent
    public ResponseEntity<Boolean> isAttendanceChecked(@PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to verify attendance of a student for exam with id: {}", examId);
        examAccessService.checkCourseAndExamAccessForStudentElseThrow(courseId, examId);
        String login = SecurityUtils.getCurrentUserLogin().orElseThrow(() -> new EntityNotFoundException("ERROR: No current user login found!"));
        return ResponseEntity.ok().body(examUserRepository.isAttendanceChecked(examId, login));
    }

}
