package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * REST controller for managing ExamUser.
 */
@RestController
@RequestMapping("api/")
public class ExamUserResource {

    private final Logger log = LoggerFactory.getLogger(ExamUserResource.class);

    private final UserRepository userRepository;

    private final ExamUserRepository examUserRepository;

    private final FileService fileService;

    private final ExamAccessService examAccessService;

    public ExamUserResource(UserRepository userRepository, FileService fileService, ExamAccessService examAccessService, ExamUserRepository examUserRepository) {
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.examUserRepository = examUserRepository;
        this.examAccessService = examAccessService;
    }

    /**
     * POST /courses/:courseId/exams/:examId/exam-users : Update the exam user with the given DTO info
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param examUserDTO  the dto containing exam user info
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamUser> updateExamUser(@RequestParam(value = "file") MultipartFile file, @PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody ExamUserDTO examUserDTO) {
        log.debug("REST request to update {} as exam user to exam : {}", examUserDTO.login(), examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(examUserDTO.login())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + examUserDTO.login() + "\" does not exist"));

        String responsePath = fileService.handleSaveFile(file, true, false);

        ExamUser examUser = examUserRepository.findByExamIdAndUser(examId, student);
        examUser.setDidCheckImage(examUserDTO.didCheckImage());
        examUser.setDidCheckLogin(examUserDTO.didCheckLogin());
        examUser.setDidCheckName(examUserDTO.didCheckName());
        examUser.setDidCheckRegistrationNumber(examUserDTO.didCheckRegistrationNumber());
        examUser.setSigningImagePath(responsePath);
        examUserRepository.save(examUser);
        return ResponseEntity.ok().body(examUser);
    }
}
