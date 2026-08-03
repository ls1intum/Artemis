package de.tum.cit.aet.artemis.iris.service;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisAssessment;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdict;
import de.tum.cit.aet.artemis.iris.domain.askuser.IrisVerdictReview;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentProgrammingStudentParticipationProjection;
import de.tum.cit.aet.artemis.iris.dto.IrisAssessmentReviewSearchDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQAExchangeDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisQuizTimerDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisVerdictDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisAssessmentRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisAssessmentQuizWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

/**
 * Service for managing state and result of an iris assessment and the in-class quiz mode management by the instructor.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisAssessmentReviewService {

    private final IrisAssessmentRepository irisAssessmentRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService;

    private final IrisSettingsService irisSettingsService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> availableInClassQuizTimers = new ConcurrentHashMap<>();

    public IrisAssessmentReviewService(IrisAssessmentRepository irisAssessmentRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, IrisChatSessionRepository irisChatSessionRepository,
            StudentParticipationRepository studentParticipationRepository, IrisAssessmentQuizWebsocketService irisAssessmentQuizWebsocketService,
            IrisSettingsService irisSettingsService, ProgrammingExerciseRepository programmingExerciseRepository, @Qualifier("taskScheduler") TaskScheduler taskScheduler) {
        this.irisAssessmentRepository = irisAssessmentRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.irisAssessmentQuizWebsocketService = irisAssessmentQuizWebsocketService;
        this.irisSettingsService = irisSettingsService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.taskScheduler = taskScheduler;
    }

    private static final String FILTER_ACCEPTED = "Accepted";

    private static final String FILTER_REJECTED = "Rejected";

    private static final String FILTER_UNSUSPICIOUS = "Unsuspicious";

    private static final String FILTER_SUSPICIOUS = "Suspicious";

    private static final String FILTER_MISSING = "MissingAssessment";

    private static final String FILTER_ALL = "All";

    private static final List<String> FILTER_KEYS = List.of(FILTER_ACCEPTED, FILTER_REJECTED, FILTER_UNSUSPICIOUS, FILTER_SUSPICIOUS, FILTER_MISSING);

    /**
     * Saves the Iris verdict for a user's assessment.
     *
     * @param user       the assessed user
     * @param exercise   the exercise
     * @param verdictDTO the verdict payload
     * @param inClass    whether to use the in-class assessment
     */
    public void saveAndHandleVerdict(User user, Exercise exercise, IrisVerdictDTO verdictDTO, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, true);

        assessment.setVerdict(verdictDTO.verdict());
        addReasoningInternal(assessment, verdictDTO.reasoning());
        // Reset review status because of new verdict
        assessment.setVerdictReview(null);

        irisAssessmentRepository.save(assessment);
    }

    public void addReasoning(User user, Exercise exercise, String reasoning, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, true);
        addReasoningInternal(assessment, reasoning);
        irisAssessmentRepository.save(assessment);
    }

    private void addReasoningInternal(IrisAssessment assessment, String reasoning) {
        var reasonings = assessment.getReasoning() == null ? new ArrayList<String>() : assessment.getReasoning();

        reasonings.add(reasoning);
        assessment.setReasoning(reasonings);
    }

    public boolean assessmentAttentionNeededInCourse(long courseId) {
        return irisAssessmentRepository.existsByCourseIdAndVerdictAndVerdictReviewIsNull(courseId, IrisVerdict.SUSPICIOUS);
    }

    /**
     * Finds Iris assessment review participations in a course using server-side pagination, search, and verdict filtering.
     *
     * @param courseId the course to search in
     * @param search   search parameters including pagination, search term, and selected verdict filters
     * @param inClass  whether to use the in-class Iris assessment relation
     * @return the paged participations and filter counts for the current text search
     */
    public IrisAssessmentReviewSearchResult findAssessmentReviewParticipationsForCourse(long courseId, IrisAssessmentReviewSearchDTO search, boolean inClass) {
        Pageable pageable = PageRequest.of(search.page(), search.pageSize());
        FilterSelection selectedFilters = FilterSelection.from(search.filterProps());
        String searchPattern = likePattern(search.searchTerm());

        Page<Long> idPage = programmingExerciseStudentParticipationRepository.findIrisAssessmentReviewParticipationIds(courseId, searchPattern, inClass,
                selectedFilters.hasSelectedFilter(), selectedFilters.accepted(), selectedFilters.rejected(), selectedFilters.unsuspicious(), selectedFilters.suspicious(),
                selectedFilters.missing(), pageable);

        Map<String, Long> participationsPerFilter = countParticipationsPerFilter(courseId, searchPattern, inClass);
        List<Long> ids = idPage.getContent();
        if (ids.isEmpty()) {
            return new IrisAssessmentReviewSearchResult(new PageImpl<>(List.of(), pageable, idPage.getTotalElements()), participationsPerFilter);
        }

        Set<IrisAssessmentProgrammingStudentParticipationProjection> projections = inClass
                ? programmingExerciseStudentParticipationRepository.findAllIrisAssessmentInClassParticipationProjectionsByIdIn(Set.copyOf(ids))
                : programmingExerciseStudentParticipationRepository.findAllIrisAssessmentParticipationProjectionsByIdIn(Set.copyOf(ids));

        Map<Long, IrisAssessmentProgrammingStudentParticipationProjection> projectionById = projections.stream()
                .collect(Collectors.toMap(IrisAssessmentProgrammingStudentParticipationProjection::id, Function.identity()));
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(ids);

        List<IrisAssessmentProgrammingStudentParticipationDTO> dtos = ids.stream().map(projectionById::get).filter(Objects::nonNull)
                .map(projection -> projection.toDto(submissionCountMap.get(projection.id()))).toList();

        return new IrisAssessmentReviewSearchResult(new PageImpl<>(dtos, pageable, idPage.getTotalElements()), participationsPerFilter);
    }

    /**
     * Finds all non-practice participations of one programming exercise whose latest result has a positive score.
     *
     * @param exerciseId the exercise id
     * @param inClass    whether to use the in-class Iris assessment relation
     * @return matching participation DTOs
     */
    public Set<IrisAssessmentProgrammingStudentParticipationDTO> findAllNonPracticeParticipationsNonZeroLatestScoreForExercise(long exerciseId, boolean inClass) {
        Set<IrisAssessmentProgrammingStudentParticipationProjection> participationProjections = inClass
                ? programmingExerciseStudentParticipationRepository
                        .findAllNonPracticeIrisAssessmentInClassParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(exerciseId)
                : programmingExerciseStudentParticipationRepository
                        .findAllNonPracticeIrisAssessmentParticipationProjectionsByExerciseIdAndLatestResultScoreGreaterThanZero(exerciseId);
        if (participationProjections.isEmpty()) {
            return Set.of();
        }

        List<Long> participationIds = participationProjections.stream().map(IrisAssessmentProgrammingStudentParticipationProjection::id).toList();
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByIdsAsMap(participationIds);

        return participationProjections.stream().map(projection -> projection.toDto(submissionCountMap.get(projection.id()))).collect(Collectors.toSet());
    }

    private Map<String, Long> countParticipationsPerFilter(long courseId, String searchPattern, boolean inClass) {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(FILTER_ALL, countParticipations(courseId, searchPattern, inClass, FilterSelection.none()));

        for (String filter : FILTER_KEYS) {
            counts.put(filter, countParticipations(courseId, searchPattern, inClass, FilterSelection.from(filter)));
        }

        return counts;
    }

    private long countParticipations(long courseId, String searchPattern, boolean inClass, FilterSelection filterSelection) {
        return programmingExerciseStudentParticipationRepository
                .findIrisAssessmentReviewParticipationIds(courseId, searchPattern, inClass, filterSelection.hasSelectedFilter(), filterSelection.accepted(),
                        filterSelection.rejected(), filterSelection.unsuspicious(), filterSelection.suspicious(), filterSelection.missing(), PageRequest.of(0, 1))
                .getTotalElements();
    }

    private static String likePattern(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return null;
        }
        return "%" + searchTerm.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /**
     * Clears verdict and reasoning for a user's assessment.
     *
     * @param user     the assessed user
     * @param exercise the exercise
     * @param inClass  whether to use the in-class assessment
     */
    public void resetVerdictAndReasoning(User user, Exercise exercise, boolean inClass) {
        IrisAssessment assessment = findOrCreateAssessment(user, exercise, inClass, true);
        resetVerdictAndReasoningInternal(assessment);
        irisAssessmentRepository.save(assessment);
    }

    public void resetVerdictAndReasoning(IrisAssessment assessment) {
        var assessmentWithReasoning = assessment.getId() == null ? assessment : irisAssessmentRepository.findWithReasoningById(assessment.getId()).orElse(assessment);
        resetVerdictAndReasoningInternal(assessmentWithReasoning);
        irisAssessmentRepository.save(assessmentWithReasoning);
    }

    private void resetVerdictAndReasoningInternal(IrisAssessment assessment) {
        assessment.setVerdict(null);
        if (assessment.getReasoning() == null) {
            assessment.setReasoning(new ArrayList<>());
        }
        else {
            assessment.getReasoning().clear();
        }
    }

    /**
     * Accepts the answers in the given {@link IrisAssessment} by updating the review status accordingly.
     *
     * @param assessment the assessment to update
     * @throws ConflictException if the verdict saved in assessment is invalid
     */
    public void acceptAnswers(IrisAssessment assessment) {
        // If answers were already accepted, nothing must be done
        if (assessment.getVerdictReview() == IrisVerdictReview.ACCEPTED) {
            return;
        }

        if (assessment.getVerdict() == null) {
            throw new ConflictException("Tried to accept answers for assessment where verdict is null", "Iris", "irisAssessmentVerdictMissing");
        }

        assessment.setVerdictReview(IrisVerdictReview.ACCEPTED);
        irisAssessmentRepository.save(assessment);
    }

    /**
     * Rejects the answers in the given {@link IrisAssessment} by updating the review status accordingly.
     *
     * @param assessment the assessment to update
     * @throws ConflictException if the verdict saved in assessment is invalid
     */
    public void rejectAnswers(IrisAssessment assessment) {
        // If answers were already rejected, nothing must be done
        if (assessment.getVerdictReview() == IrisVerdictReview.REJECTED) {
            return;
        }

        if (assessment.getVerdict() == null) {
            throw new ConflictException("Tried to reject answers for assessment where verdict is null", "Iris", "irisAssessmentVerdictMissing");
        }

        assessment.setVerdictReview(IrisVerdictReview.REJECTED);
        irisAssessmentRepository.save(assessment);
    }

    /**
     * Creates a regular Iris assessment for the given participation.
     *
     * @param participation the participation
     * @return the created assessment
     */
    public IrisAssessment createNewAssessment(ProgrammingExerciseStudentParticipation participation) {
        return createNewAssessment(participation, false);
    }

    /**
     * Creates an Iris assessment for the given participation.
     *
     * @param participation the participation
     * @param inClass       whether to create an in-class assessment
     * @return the created assessment
     */
    public IrisAssessment createNewAssessment(ProgrammingExerciseStudentParticipation participation, boolean inClass) {
        if (participation.isPracticeMode()) {
            throw new IllegalStateException("Tried to create an assessment for a practice participation");
        }

        var student = participation.getStudent().orElseThrow();
        var exercise = participation.getExercise();

        var newAssessment = irisAssessmentRepository.save(new IrisAssessment(student, exercise));

        if (inClass) {
            participation.setIrisAssessmentInClass(newAssessment);
        }
        else {
            participation.setIrisAssessment(newAssessment);
        }
        programmingExerciseStudentParticipationRepository.save(participation);

        return newAssessment;

    }

    /**
     * Deletes all in-class Iris assessments for an exercise and clears the participation references first.
     *
     * @param exercise the programming exercise
     */
    private void deleteInClassAssessmentsForExercise(ProgrammingExercise exercise) {
        var assessmentIds = programmingExerciseStudentParticipationRepository.findIrisAssessmentInClassIdsByExerciseId(exercise.getId());
        if (assessmentIds.isEmpty()) {
            return;
        }

        programmingExerciseStudentParticipationRepository.unsetIrisAssessmentInClassByExerciseId(exercise.getId());
        irisAssessmentRepository.deleteAllByIdInBulk(assessmentIds);
    }

    /**
     * Returns the question-answer exchanges for a completed ask-user-mode assessment.
     *
     * @param assessment the Iris assessment
     * @param exercise   the exercise
     * @param user       the student
     * @param inClass    whether the chat is part of an in-class quiz session
     * @return the ordered question-answer exchanges
     */
    public List<IrisQAExchangeDTO> getQAExchangeDTOList(IrisAssessment assessment, Exercise exercise, User user, boolean inClass) {
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new ConflictException("Ask-user mode is only supported for programming exercises", "Iris", "irisExerciseTypeUnsupported");
        }

        var session = irisChatSessionRepository.findLatestFinishedAskUserModeSessionByExerciseIdAndUserIdAndInClassQuizElseThrow(exercise.getId(), user.getId(), inClass);
        if (assessment == null) {
            throw new ConflictException("Iris Assessment is missing so QAExchangeList cannot be retrieved", "Iris", "irisAssessmentMissing");
        }
        var reasoning = assessment.getReasoning();
        if (reasoning == null || reasoning.isEmpty()) {
            throw new ConflictException("Iris reasoning is missing for assessment", "Iris", "irisReasoningMissing");
        }

        // skip first and drop last message because quiz explanation and quiz_finished messages are not needed
        List<IrisMessage> llmMessages = session.getMessages().stream().filter(message -> message.getSender().equals(IrisMessageSender.LLM) && message.getInAskUserMode()).skip(1)
                .toList();
        List<IrisMessage> irisMessages = llmMessages.isEmpty() ? List.of() : llmMessages.subList(0, llmMessages.size() - 1);
        List<IrisMessage> userMessages = session.getMessages().stream().filter(message -> message.getSender().equals(IrisMessageSender.USER) && message.getInAskUserMode())
                .toList();

        int maxSize = Math.max(Math.max(irisMessages.size(), userMessages.size()), reasoning.size());

        return IntStream.range(0, maxSize).mapToObj(i -> new IrisQAExchangeDTO(i, i < irisMessages.size() ? irisMessages.get(i).getContent().getFirst().getContentAsString() : "",
                i < userMessages.size() ? userMessages.get(i).getContent().getFirst().getContentAsString() : "", i < reasoning.size() ? reasoning.get(i) : "")).toList();
    }

    public void validateInClassQuizIsAvailableOrElseThrow(ProgrammingExercise exercise) {
        if (getAvailableInClassQuiz(exercise) == null) {
            throw new ConflictException("The in-class quiz timer has expired or is not active", "Iris", "irisInClassQuizExpired");
        }
    }

    /**
     * Returns the currently active in-class quiz timer for an exercise.
     *
     * @param exercise the programming exercise
     * @return timer information or null if no active timer exists
     */
    public IrisQuizTimerDTO getAvailableInClassQuiz(ProgrammingExercise exercise) {
        var expiresAt = exercise.getIrisInClassQuizTimer();
        if (expiresAt == null) {
            return null;
        }

        var now = ZonedDateTime.now();
        if (!expiresAt.isAfter(now)) {
            clearInClassQuizTimer(exercise, expiresAt);
            return null;
        }

        var remainingSeconds = Math.max(Duration.between(now, expiresAt).toSeconds(), 0);
        return new IrisQuizTimerDTO(expiresAt, Math.toIntExact(remainingSeconds));
    }

    /**
     * Makes the in-class quiz mode available for students for an exercise.
     *
     * @param exercise the exercise for which the in-class quiz should be made available
     * @return timer information for the active in-class quiz window
     */
    public IrisQuizTimerDTO makeInClassQuizAvailable(ProgrammingExercise exercise) {
        if (exercise.isExamExercise()) {
            throw new ConflictException("Iris Ask-user Mode is not supported for exam exercises", "Iris", "irisExamExercise");
        }
        irisSettingsService.ensureAskUserModeEnabledForExerciseOrElseThrow(exercise);
        deleteInClassAssessmentsForExercise(exercise);

        var settings = irisSettingsService.getSettingsForExercise(exercise).askUserModeSettings();
        var timeLimit = settings.timeLimitInClass() * 60;
        var expiresAt = ZonedDateTime.now().plusMinutes(settings.timeLimitInClass());

        exercise.setIrisInClassQuizTimer(expiresAt);
        programmingExerciseRepository.save(exercise);
        scheduleInClassQuizTimerCleanup(exercise.getId(), expiresAt);
        irisAssessmentQuizWebsocketService.sendInClassQuizStarted(exercise.getId());

        return new IrisQuizTimerDTO(expiresAt, timeLimit);
    }

    private void scheduleInClassQuizTimerCleanup(long exerciseId, ZonedDateTime expiresAt) {
        var previousTimer = availableInClassQuizTimers.remove(exerciseId);
        if (previousTimer != null) {
            previousTimer.cancel(false);
        }

        var future = taskScheduler.schedule(() -> programmingExerciseRepository.findById(exerciseId).ifPresent(exercise -> clearInClassQuizTimer(exercise, expiresAt)),
                expiresAt.toInstant());
        availableInClassQuizTimers.put(exerciseId, future);
    }

    private void clearInClassQuizTimer(ProgrammingExercise exercise, ZonedDateTime expectedExpiresAt) {
        if (Objects.equals(exercise.getIrisInClassQuizTimer(), expectedExpiresAt)) {
            exercise.setIrisInClassQuizTimer(null);
            programmingExerciseRepository.save(exercise);
        }
        availableInClassQuizTimers.remove(exercise.getId());
    }

    private IrisAssessment findOrCreateAssessment(User user, Exercise exercise, boolean inClass, boolean withReasoning) {
        var participation = programmingExerciseStudentParticipationRepository
                .findWithIrisAssessmentByExerciseIdAndStudentLoginAndTestRun(exercise.getId(), user.getLogin(), inClass, false).orElseThrow();
        var assessment = inClass ? participation.getIrisAssessmentInClass() : participation.getIrisAssessment();

        if (assessment == null) {
            return createNewAssessment(participation, inClass);
        }

        if (withReasoning) {
            return irisAssessmentRepository.findWithReasoningById(assessment.getId()).orElseThrow();
        }

        return assessment;
    }

    private record FilterSelection(boolean accepted, boolean rejected, boolean unsuspicious, boolean suspicious, boolean missing) {

        static FilterSelection none() {
            return new FilterSelection(false, false, false, false, false);
        }

        static FilterSelection from(String filterProps) {
            if (filterProps == null || filterProps.isBlank()) {
                return none();
            }

            List<String> filters = Arrays.stream(filterProps.split(",")).map(String::trim).filter(filter -> !filter.isBlank()).toList();
            return new FilterSelection(filters.contains(FILTER_ACCEPTED), filters.contains(FILTER_REJECTED), filters.contains(FILTER_UNSUSPICIOUS),
                    filters.contains(FILTER_SUSPICIOUS), filters.contains(FILTER_MISSING));
        }

        boolean hasSelectedFilter() {
            return accepted || rejected || unsuspicious || suspicious || missing;
        }
    }

    public record IrisAssessmentReviewSearchResult(Page<IrisAssessmentProgrammingStudentParticipationDTO> page, Map<String, Long> participationsPerFilter) {
    }
}
