package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.ExamUserRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastInstructor;
import de.tum.in.www1.artemis.service.FilePathService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.exam.ExamAccessService;
import de.tum.in.www1.artemis.service.exam.ExamUserService;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserAttendanceCheckDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamUsersNotFoundDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ExamUser.
 */
@Profile(PROFILE_CORE)
@RestController
public class ExamUserResource {

    private static final Logger log = LoggerFactory.getLogger(ExamUserResource.class);

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final FileService fileService;

    private final ExamAccessService examAccessService;

    private final ExamUserService examUserService;

    private final FilePathService filePathURI;

    public ExamUserResource(ExamUserService examUserService, UserRepository userRepository, FileService fileService, ExamAccessService examAccessService,
            ExamUserRepository examUserRepository, FilePathService filePathURI) {
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.examUserRepository = examUserRepository;
        this.examAccessService = examAccessService;
        this.examUserService = examUserService;
        this.filePathURI = filePathURI;
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
}
