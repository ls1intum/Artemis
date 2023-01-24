package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.exam.ExamUser;
import de.tum.in.www1.artemis.repository.*;
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

    private final CourseRepository courseRepository;

    private final ExamUserRepository examUserRepository;

    private final ExamRepository examRepository;

    private final ExamAccessService examAccessService;

    private final ExamRegistrationService examRegistrationService;

    public ExamUserResource(UserRepository userRepository, ExamRepository examRepository, ExamAccessService examAccessService, CourseRepository courseRepository,
            ExamUserRepository examUserRepository, ExamRegistrationService examRegistrationService) {
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
        this.examUserRepository = examUserRepository;
        this.examRegistrationService = examRegistrationService;
        this.examAccessService = examAccessService;
        this.examRepository = examRepository;
    }

    /**
     * POST /courses/:courseId/exams/:examId/exam-users : Update the exam user with the given dto info
     *
     * @param courseId     the id of the course
     * @param examId       the id of the exam
     * @param examUserDTO  the dto containing exam user info
     * @return empty ResponseEntity with status 200 (OK) or with status 404 (Not Found)
     */
    @PostMapping("courses/{courseId}/exams/{examId}/exam-users")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ExamUser> updateExamUser(@PathVariable Long courseId, @PathVariable Long examId, @RequestBody ExamUserDTO examUserDTO) {
        log.debug("REST request to update {} as exam user to exam : {}", examUserDTO.login(), examId);

        examAccessService.checkCourseAndExamAccessForInstructorElseThrow(courseId, examId);
        // var course = courseRepository.findByIdElseThrow(courseId);
        // var exam = examRepository.findByIdWithExamUsersElseThrow(examId);

        var student = userRepository.findOneWithGroupsAndAuthoritiesByLogin(examUserDTO.login())
                .orElseThrow(() -> new EntityNotFoundException("User with login: \"" + examUserDTO.login() + "\" does not exist"));

        ExamUser examUser = examUserRepository.findByExamIdAndUser(examId, student);

        // todo: get info from dto
        examUser.setDidCheckImage(true);
        examUser.setDidCheckLogin(true);
        examUser.setDidCheckName(true);
        examUser.setDidCheckRegistrationNumber(true);
        examUserRepository.save(examUser);
        return ResponseEntity.ok().body(examUser);
    }
}
