package de.tum.cit.aet.artemis.iris.service.session;

import static de.tum.cit.aet.artemis.core.util.TimeUtil.toInstant;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.LearningMetricsApi;
import de.tum.cit.aet.artemis.atlas.dto.metrics.StudentMetricsDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.chat.PyrisChatPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisCourseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisLectureUnitDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisProgrammingExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisSubmissionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisTextExerciseDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.data.PyrisUserDTO;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureApiNotPresentException;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.text.api.TextApi;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

/**
 * Factory responsible for building {@link PyrisChatPipelineExecutionDTO} instances for Iris chat pipelines.
 * Encapsulates all data-loading logic required to populate the execution DTO based on the active chat mode.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatPipelineDTOFactory {

    private final UserRepository userRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<LearningMetricsApi> learningMetricsApi;

    private final PyrisDTOService pyrisDTOService;

    private final PyrisPipelineService pyrisPipelineService;

    public IrisChatPipelineDTOFactory(UserRepository userRepository, ProgrammingExerciseRepository programmingExerciseRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ProgrammingSubmissionRepository programmingSubmissionRepository,
            StudentParticipationRepository studentParticipationRepository, Optional<TextRepositoryApi> textRepositoryApi, Optional<LectureRepositoryApi> lectureRepositoryApi,
            Optional<LearningMetricsApi> learningMetricsApi, PyrisDTOService pyrisDTOService, PyrisPipelineService pyrisPipelineService) {
        this.userRepository = userRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.textRepositoryApi = textRepositoryApi;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.learningMetricsApi = learningMetricsApi;
        this.pyrisDTOService = pyrisDTOService;
        this.pyrisPipelineService = pyrisPipelineService;
    }

    /**
     * Builds the {@link PyrisChatPipelineExecutionDTO} for the given chat context.
     * Loads mode-specific data (exercise, lecture, submission) on top of the shared course and metrics base.
     *
     * @param chatMode           the chat mode determining which context data to load
     * @param session            the active chat session
     * @param executionDto       the pipeline execution DTO received from Pyris
     * @param customInstructions optional custom instructions to include in the pipeline
     * @param course             the course the session belongs to
     * @param latestSubmission   the latest programming submission, if already resolved by the caller
     * @param uncommittedFiles   uncommitted file changes provided by the client
     * @return the fully populated {@link PyrisChatPipelineExecutionDTO}
     */
    public PyrisChatPipelineExecutionDTO buildChatDTO(IrisChatMode chatMode, IrisChatSession session, PyrisPipelineExecutionDTO executionDto, String customInstructions,
            Course course, Optional<ProgrammingSubmission> latestSubmission, Map<String, String> uncommittedFiles) {
        var user = userRepository.findByIdElseThrow(session.getUserId());
        var messages = pyrisDTOService.toPyrisMessageDTOList(session.getMessages());

        // Base data shared across all chat modes (course chat is the baseline)
        var fullCourse = pyrisPipelineService.loadCourseWithParticipationOfStudent(course.getId(), session.getUserId());
        PyrisCourseDTO courseDto = PyrisCourseDTO.of(fullCourse);
        StudentMetricsDTO metrics = learningMetricsApi.map(api -> api.getStudentCourseMetrics(session.getUserId(), course.getId())).orElse(null);

        // Mode-specific fields (additive on top of base data)
        PyrisProgrammingExerciseDTO programmingExercise = null;
        PyrisTextExerciseDTO textExercise = null;
        PyrisLectureDTO lectureDto = null;
        PyrisSubmissionDTO progSubmission = null;
        String textSubmission = null;

        switch (chatMode) {
            case PROGRAMMING_EXERCISE_CHAT -> {
                var progExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(session.getEntityId());
                programmingExercise = pyrisDTOService.toPyrisProgrammingExerciseDTO(progExercise);
                var actualSubmission = latestSubmission.or(() -> getLatestSubmissionIfExists(progExercise, user));
                progSubmission = actualSubmission.map(s -> pyrisDTOService.toPyrisSubmissionDTO(s, uncommittedFiles)).orElse(null);
            }
            case TEXT_EXERCISE_CHAT -> {
                var exercise = textRepositoryApi.orElseThrow(() -> new TextApiNotPresentException(TextApi.class)).findByIdElseThrow(session.getEntityId());
                textExercise = PyrisTextExerciseDTO.of(exercise);
                var participation = studentParticipationRepository.findWithEagerSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());
                var latest = participation.flatMap(p -> p.getSubmissions().stream().max(Comparator.comparingLong(Submission::getId))).orElse(null);
                textSubmission = latest instanceof TextSubmission ts ? ts.getText() : null;
            }
            case LECTURE_CHAT -> {
                var api = lectureRepositoryApi.orElseThrow(() -> new LectureApiNotPresentException(LectureRepositoryApi.class));
                var lecture = api.findByIdWithLectureUnitsElseThrow(session.getEntityId());
                Long courseId = course.getId();
                List<PyrisLectureUnitDTO> lectureUnits = lecture.getLectureUnits() == null ? List.of() : lecture.getLectureUnits().stream().map(unit -> {
                    Integer attachmentVersion = null;
                    if (unit instanceof AttachmentVideoUnit attachmentUnit && attachmentUnit.getAttachment() != null && attachmentUnit.getAttachment().getVersion() != null) {
                        attachmentVersion = attachmentUnit.getAttachment().getVersion();
                    }
                    return new PyrisLectureUnitDTO(unit.getId(), courseId, lecture.getId(), toInstant(unit.getReleaseDate()), unit.getName(), attachmentVersion);
                }).toList();
                lectureDto = new PyrisLectureDTO(lecture.getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(), lecture.getEndDate(), lectureUnits);
            }
            case COURSE_CHAT -> {
                // All data already loaded in the base section above
            }
            default -> throw new IllegalArgumentException("IrisChatPipelineDTOFactory does not support chat mode " + chatMode);
        }

        return new PyrisChatPipelineExecutionDTO(chatMode, messages, executionDto.settings(), session.getTitle(), new PyrisUserDTO(user), executionDto.initialStages(),
                customInstructions, courseDto, programmingExercise, textExercise, lectureDto, null, progSubmission, textSubmission, metrics, null);
    }

    private Optional<ProgrammingSubmission> getLatestSubmissionIfExists(ProgrammingExercise exercise, User user) {
        var participations = exercise.isTeamMode()
                ? programmingExerciseStudentParticipationRepository.findAllWithSubmissionByExerciseIdAndStudentLoginInTeam(exercise.getId(), user.getLogin())
                : programmingExerciseStudentParticipationRepository.findAllWithSubmissionsByExerciseIdAndStudentLogin(exercise.getId(), user.getLogin());

        if (participations.isEmpty()) {
            return Optional.empty();
        }
        return participations.getLast().getSubmissions().stream().max(Submission::compareTo)
                .flatMap(sub -> programmingSubmissionRepository.findWithEagerResultsAndFeedbacksAndBuildLogsById(sub.getId()));
    }
}
