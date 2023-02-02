package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.FileService;
import de.tum.in.www1.artemis.service.exam.*;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserDTO;
import de.tum.in.www1.artemis.web.rest.dto.ExamUserWithImageDTO;
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

    private final AuthorizationCheckService authorizationCheckService;

    private final CourseRepository courseRepository;

    private final ExamUserService examUserService;

    public ExamUserResource(ExamUserService examUserService, AuthorizationCheckService authorizationCheckService, UserRepository userRepository, FileService fileService,
            ExamAccessService examAccessService, ExamUserRepository examUserRepository, CourseRepository courseRepository) {
        this.userRepository = userRepository;
        this.fileService = fileService;
        this.examUserRepository = examUserRepository;
        this.examAccessService = examAccessService;
        this.authorizationCheckService = authorizationCheckService;
        this.courseRepository = courseRepository;
        this.examUserService = examUserService;
    }

    /**
     * POST /courses/:courseId/exams/:examId/exam-users : Update the exam user with the given DTO info
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param examUserDTO  the dto containing exam user info
     * @return saved examUser ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamUser> updateExamUser(@RequestPart ExamUserDTO examUserDTO, @RequestPart(value = "file", required = false) MultipartFile file,
            @PathVariable Long courseId, @PathVariable Long examId) {
        log.debug("REST request to update {} as exam user to exam : {}", examUserDTO.login(), examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(examUserDTO.login())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + examUserDTO.login() + "\" does not exist"));

        ExamUser examUser = examUserRepository.findByExamIdAndUserId(examId, student.getId());

        if (examUser == null) {
            throw new EntityNotFoundException("Exam user", examUserDTO.login());
        }

        if (file != null) {
            String responsePath = fileService.handleSaveFile(file, true, false);
            examUser.setSigningImagePath(responsePath);
        }
        examUser.setDidCheckImage(examUserDTO.didCheckImage());
        examUser.setDidCheckLogin(examUserDTO.didCheckLogin());
        examUser.setDidCheckName(examUserDTO.didCheckName());
        examUser.setDidCheckRegistrationNumber(examUserDTO.didCheckRegistrationNumber());
        examUser.setActualSeat(examUserDTO.seat());
        examUser.setActualRoom(examUserDTO.room());
        examUser = examUserRepository.save(examUser);

        return ResponseEntity.ok().body(examUser);
    }

    /**
     * POST courses/{courseId}/exams/{examId}/exam-users-parse-pdf : Parse pdf and get exam user data
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param file         the pdf file to parse
     * @return list of examUsersWithImage ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users-parse-pdf")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ExamUserWithImageDTO>> getUsersDataFromPDF(@PathVariable Long courseId, @PathVariable Long examId, @RequestParam("file") MultipartFile file) {
        log.debug("REST request to parse pdf : {}", file.getOriginalFilename());
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        return ResponseEntity.ok().body(examUserService.parsePDF(file));
    }

    /**
     * POST courses/{courseId}/exams/{examId}/exam-users-save-image : save exam user image
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @return list of not found examUsersWithImage ResponseEntity with status 200 (OK)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users-save-image")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<List<ExamUserWithImageDTO>> saveUsersImage(@PathVariable Long courseId, @PathVariable Long examId,
            @RequestBody List<ExamUserWithImageDTO> examUserWithImageDTOs) {
        log.debug("REST request to parse pdf : {}", examUserWithImageDTOs);
        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        return ResponseEntity.ok().body(examUserService.saveImages(examId, examUserWithImageDTOs));
    }
}
