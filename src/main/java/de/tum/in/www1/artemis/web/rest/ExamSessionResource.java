package de.tum.in.www1.artemis.web.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.service.ExamSessionService;
import de.tum.in.www1.artemis.service.StudentExamService;
import de.tum.in.www1.artemis.service.user.UserService;

/**
 * REST controller for managing ExamSessions.
 */
@RestController
@RequestMapping("/api")
public class ExamSessionResource {

    private final Logger log = LoggerFactory.getLogger(ExamSessionResource.class);

    private final ExamSessionService examSessionService;

    private final UserService userService;

    private final StudentExamService studentExamService;

    public ExamSessionResource(ExamSessionService examSessionService, UserService userService, StudentExamService studentExamService) {
        this.examSessionService = examSessionService;
        this.userService = userService;
        this.studentExamService = studentExamService;

    }

}
