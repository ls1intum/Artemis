package de.tum.cit.aet.artemis.fileupload;

import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.assessment.repository.ComplaintRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.ComplaintUtilService;
import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.test_repository.CompetencyExerciseLinkTestRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadExerciseRepository;
import de.tum.cit.aet.artemis.fileupload.repository.FileUploadSubmissionRepository;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

public abstract class AbstractFileUploadIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    // Repositories
    @Autowired
    protected FileUploadExerciseRepository fileUploadExerciseRepository;

    // External Repositories
    @Autowired
    protected ComplaintRepository complaintRepository;

    @Autowired
    protected SubmissionTestRepository submissionRepository;

    @Autowired
    protected ExamRepository examRepository;

    @Autowired
    protected StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    protected FeedbackRepository feedbackRepository;

    @Autowired
    protected GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    protected ChannelRepository channelRepository;

    @Autowired
    protected ParticipationTestRepository participationRepository;

    @Autowired
    protected CompetencyExerciseLinkTestRepository competencyExerciseLinkRepository;

    // Services
    @Autowired
    protected FileUploadSubmissionRepository fileUploadSubmissionRepository;

    // Util Services
    @Autowired
    protected FileUploadExerciseUtilService fileUploadExerciseUtilService;

    // External Util Services
    @Autowired
    protected ParticipationUtilService participationUtilService;

    @Autowired
    protected ExamUtilService examUtilService;

    @Autowired
    protected ComplaintUtilService complaintUtilService;

    @Autowired
    protected PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    protected CompetencyUtilService competencyUtilService;

    @Autowired
    protected ModelingExerciseUtilService modelingExerciseUtilService;
}
