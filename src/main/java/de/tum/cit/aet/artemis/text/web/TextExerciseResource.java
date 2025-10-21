package de.tum.cit.aet.artemis.text.web;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.ExampleSubmission;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.repository.TextBlockRepository;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfigHelper;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseService;

/**
 * REST controller for managing TextExercise.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
public class TextExerciseResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseResource.class);

    private static final String ENTITY_NAME = "textExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final TextExerciseService textExerciseService;

    private final ExerciseService exerciseService;

    private final ExerciseDeletionService exerciseDeletionService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExamAccessApi> examAccessApi;

    private final FeedbackRepository feedbackRepository;

    private final TextBlockRepository textBlockRepository;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ExampleSubmissionRepository exampleSubmissionRepository;

    private final GradingCriterionRepository gradingCriterionRepository;

    private final CourseRepository courseRepository;

    private final ChannelRepository channelRepository;

    public TextExerciseResource(TextExerciseRepository textExerciseRepository, TextExerciseService textExerciseService, FeedbackRepository feedbackRepository,
            ExerciseDeletionService exerciseDeletionService, UserRepository userRepository, AuthorizationCheckService authCheckService,
            StudentParticipationRepository studentParticipationRepository, ExampleSubmissionRepository exampleSubmissionRepository, ExerciseService exerciseService,
            GradingCriterionRepository gradingCriterionRepository, TextBlockRepository textBlockRepository, CourseRepository courseRepository, ChannelRepository channelRepository,
            Optional<ExamAccessApi> examAccessApi) {
        this.feedbackRepository = feedbackRepository;
        this.exerciseDeletionService = exerciseDeletionService;
        this.textBlockRepository = textBlockRepository;
        this.textExerciseService = textExerciseService;
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.exerciseService = exerciseService;
        this.gradingCriterionRepository = gradingCriterionRepository;
        this.courseRepository = courseRepository;
        this.channelRepository = channelRepository;
        this.examAccessApi = examAccessApi;
    }

    /**
     * GET /courses/:courseId/exercises : get all the exercises.
     *
     * @param courseId id of the course of which all the exercises should be fetched
     * @return the ResponseEntity with status 200 (OK) and the list of textExercises in body
     */
    @GetMapping("courses/{courseId}/text-exercises")
    @EnforceAtLeastTutor
    public ResponseEntity<List<TextExercise>> getTextExercisesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all ProgrammingExercises for the course with id : {}", courseId);
        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, null);
        List<TextExercise> exercises = textExerciseRepository.findByCourseIdWithCategories(courseId);
        for (Exercise exercise : exercises) {
            // not required in the returned json body
            exercise.setStudentParticipations(null);
            exercise.setCourse(null);
            Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId());
            exercise.setGradingCriteria(gradingCriteria);
        }
        return ResponseEntity.ok().body(exercises);
    }

    private Optional<TextExercise> findTextExercise(Long exerciseId, boolean includePlagiarismDetectionConfig, boolean includeAthenaConfig) {
        if (includePlagiarismDetectionConfig && includeAthenaConfig) {
            var textExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigAndAthenaConfigById(exerciseId);
            textExercise.ifPresent(it -> PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(it, textExerciseRepository));
            return textExercise;
        }
        if (includePlagiarismDetectionConfig) {
            var textExercise = textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndPlagiarismDetectionConfigById(exerciseId);
            textExercise.ifPresent(it -> PlagiarismDetectionConfigHelper.createAndSaveDefaultIfNullAndCourseExercise(it, textExerciseRepository));
            return textExercise;
        }
        if (includeAthenaConfig) {
            return textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesAndAthenaConfigById(exerciseId);
        }
        return textExerciseRepository.findWithEagerTeamAssignmentConfigAndCategoriesAndCompetenciesById(exerciseId);
    }

    /**
     * GET /text-exercises/:id : get the "id" textExercise.
     *
     * @param exerciseId                    the id of the textExercise to retrieve
     * @param withPlagiarismDetectionConfig boolean flag whether to include the plagiarism detection config of the exercise
     * @param withAthenaConfig              boolean flag whether to include the athena config of the exercise
     * @return the ResponseEntity with status 200 (OK) and with body the textExercise, or with
     *         status 404 (Not Found)
     */
    @GetMapping("text-exercises/{exerciseId}")
    @EnforceAtLeastTutor
    public ResponseEntity<TextExercise> getTextExercise(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean withPlagiarismDetectionConfig,
            @RequestParam(defaultValue = "false") boolean withAthenaConfig) {
        log.debug("REST request to get TextExercise : {}", exerciseId);
        var textExercise = findTextExercise(exerciseId, withPlagiarismDetectionConfig, withAthenaConfig).orElseThrow(() -> new EntityNotFoundException("TextExercise", exerciseId));

        // If the exercise belongs to an exam, only editors, instructors and admins are allowed to access it
        if (textExercise.isExamExercise()) {
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, textExercise, null);
        }
        else {
            // in courses, also tutors can access the exercise
            authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, null);
        }
        if (textExercise.isCourseExercise()) {
            Channel channel = channelRepository.findChannelByExerciseId(textExercise.getId());
            if (channel != null) {
                textExercise.setChannelName(channel.getName());
            }
        }

        Set<ExampleSubmission> exampleSubmissions = this.exampleSubmissionRepository.findAllWithResultByExerciseId(exerciseId);
        Set<GradingCriterion> gradingCriteria = gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exerciseId);
        textExercise.setGradingCriteria(gradingCriteria);
        textExercise.setExampleSubmissions(exampleSubmissions);

        exerciseService.checkExerciseIfStructuredGradingInstructionFeedbackUsed(gradingCriteria, textExercise);
        return ResponseEntity.ok().body(textExercise);
    }

    /**
     * DELETE /text-exercises/:exerciseId : delete the "exerciseId" textExercise.
     *
     * @param exerciseId the id of the textExercise to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("text-exercises/{exerciseId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteTextExercise(@PathVariable Long exerciseId) {
        log.info("REST request to delete TextExercise : {}", exerciseId);
        var textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, textExercise, user);
        // NOTE: we use the exerciseDeletionService here, because this one makes sure to clean up all lazy references correctly.
        exerciseService.logDeletion(textExercise, textExercise.getCourseViaExerciseGroupOrCourseMember(), user);
        exerciseDeletionService.delete(exerciseId, false);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, textExercise.getTitle())).build();
    }

    /**
     * Returns the data needed for the text editor, which includes the participation, textSubmission
     * with answer if existing and the assessments if the submission was already submitted.
     *
     * @param participationId the participationId for which to find the data for the text editor
     * @return the ResponseEntity with the participation as body
     */
    // TODO: fix the URL scheme
    @GetMapping("text-editor/{participationId}")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getDataForTextEditor(@PathVariable Long participationId) {
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = studentParticipationRepository.findByIdWithLatestSubmissionsResultsFeedbackElseThrow(participationId);
        if (!(participation.getExercise() instanceof TextExercise textExercise)) {
            throw new BadRequestAlertException("The exercise of the participation is not a text exercise.", ENTITY_NAME, "wrongExerciseType");
        }

        // users can only see their own submission (to prevent cheating), TAs, instructors and admins can see all answers
        if (!authCheckService.isOwnerOfParticipation(participation, user) && !authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
            throw new AccessForbiddenException();
        }

        // Exam exercises cannot be seen by students between the endDate and the publishResultDate
        if (textExercise.isExamExercise()) {
            ExamAccessApi api = examAccessApi.orElseThrow(() -> new ExamApiNotPresentException(ExamAccessApi.class));
            api.checkIfAllowedToGetExamResult(textExercise, participation, user);
        }

        Set<Submission> submissions = participation.getSubmissions();
        participation.setSubmissions(new HashSet<>());

        for (Submission submission : submissions) {
            if (submission != null) {
                TextSubmission textSubmission = (TextSubmission) submission;

                // set reference to participation to null, since we are already inside a participation
                textSubmission.setParticipation(null);

                if (!ExerciseDateService.isAfterAssessmentDueDate(textExercise) && !authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
                    // We want to have the preliminary feedback before the assessment due date too
                    List<Result> athenaResults = submission.getResults().stream().filter(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA).toList();
                    textSubmission.setResults(athenaResults);
                }

                Result result = textSubmission.getLatestResult();
                if (result != null) {
                    // Load TextBlocks for the Submission. They are needed to display the Feedback in the client.
                    final var textBlocks = textBlockRepository.findAllBySubmissionId(textSubmission.getId());
                    textSubmission.setBlocks(textBlocks);

                    if (textSubmission.isSubmitted() && result.getCompletionDate() != null) {
                        List<Feedback> assessments = feedbackRepository.findByResult(result);
                        result.setFeedbacks(assessments);
                    }

                    if (!authCheckService.isAtLeastTeachingAssistantForExercise(textExercise, user)) {
                        result.filterSensitiveInformation();
                    }

                    // only send the one latest result to the client
                    textSubmission.setResults(List.of(result));
                }
                participation.addSubmission(textSubmission);
            }
        }

        // if all submissions were deleted, add a new one since the client relies on the existence of at least one submission
        if (submissions.isEmpty()) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.setParticipation(participation);
            textSubmission.setSubmitted(false);
            participation.addSubmission(textSubmission);
        }

        if (!(authCheckService.isAtLeastInstructorForExercise(textExercise, user) || participation.isOwnedBy(user))) {
            participation.filterSensitiveInformation();
        }

        textExercise.filterSensitiveInformation();
        if (textExercise.isExamExercise()) {
            textExercise.getExam().setCourse(null);
        }

        return ResponseEntity.ok(participation);
    }

    /**
     * Search for all text exercises by id, title and course title. The result is pageable since there
     * might be hundreds of exercises in the DB.
     *
     * @param search         The pageable search containing the page size, page number and query string
     * @param isCourseFilter Whether to search in the courses for exercises
     * @param isExamFilter   Whether to search in the groups for exercises
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("text-exercises")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<TextExercise>> getAllExercisesOnPage(SearchTermPageableSearchDTO<String> search,
            @RequestParam(defaultValue = "true") boolean isCourseFilter, @RequestParam(defaultValue = "true") boolean isExamFilter) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(textExerciseService.getAllOnPageWithSize(search, isCourseFilter, isExamFilter, user));
    }
}
