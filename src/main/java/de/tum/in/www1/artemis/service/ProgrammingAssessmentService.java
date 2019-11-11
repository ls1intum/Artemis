package de.tum.in.www1.artemis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.repository.ComplaintRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.repository.StudentParticipationRepository;

@Service
public class ProgrammingAssessmentService extends AssessmentService {

    private final Logger log = LoggerFactory.getLogger(ModelingAssessmentService.class);

    private final UserService userService;

    private final ProgrammingSubmissionService programmingSubmissionService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    public ProgrammingAssessmentService(UserService userService, ComplaintResponseService complaintResponseService, ProgrammingSubmissionRepository programmingSubmissionRepository,
            ComplaintRepository complaintRepository, ResultRepository resultRepository, StudentParticipationRepository studentParticipationRepository, ResultService resultService,
            AuthorizationCheckService authCheckService, ProgrammingSubmissionService programmingSubmissionService) {
        super(complaintResponseService, complaintRepository, resultRepository, studentParticipationRepository, resultService, authCheckService);
        this.userService = userService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.programmingSubmissionService = programmingSubmissionService;
    }
}
