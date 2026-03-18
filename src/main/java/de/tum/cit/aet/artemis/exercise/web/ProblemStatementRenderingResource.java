package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.dto.RenderedProblemStatementDTO;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.service.ProblemStatementRenderingService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ProblemStatementRenderingResource {

    private static final Logger log = LoggerFactory.getLogger(ProblemStatementRenderingResource.class);

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final Optional<ExamDateApi> examDateApi;

    private final ProblemStatementRenderingService renderingService;

    public ProblemStatementRenderingResource(ExerciseRepository exerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            Optional<ExamDateApi> examDateApi, ProblemStatementRenderingService renderingService) {
        this.exerciseRepository = exerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.examDateApi = examDateApi;
        this.renderingService = renderingService;
    }

    /**
     * GET exercises/{exerciseId}/problem-statement/rendered : Returns the pre-rendered HTML of an exercise's problem statement.
     *
     * @param exerciseId    the id of the exercise
     * @param selfContained if true, PlantUML diagrams are inlined as SVG (default: false)
     * @return the rendered problem statement DTO
     */
    @GetMapping(value = "exercises/{exerciseId}/problem-statement/rendered", produces = MediaType.APPLICATION_JSON_VALUE)
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<RenderedProblemStatementDTO> getRenderedProblemStatement(@PathVariable Long exerciseId, @RequestParam(defaultValue = "false") boolean selfContained) {

        log.debug("REST request to get rendered problem statement for Exercise : {}", exerciseId);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        // Authorization: mirror ExerciseResource.getExercise()
        if (exercise.isExamExercise()) {
            Exam exam = exercise.getExerciseGroup().getExam();
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            if (authCheckService.isAtLeastEditorForExercise(exercise, user)) {
                // editors, instructors, and admins always allowed
            }
            else if (authCheckService.isAtLeastTeachingAssistantForExercise(exercise, user)) {
                ZonedDateTime latestIndividualExamEndDate = api.getLatestIndividualExamEndDate(exam);
                if (latestIndividualExamEndDate == null || latestIndividualExamEndDate.isAfter(ZonedDateTime.now())) {
                    throw new AccessForbiddenException();
                }
            }
            else {
                // Students should never access exam exercises through this endpoint
                throw new AccessForbiddenException();
            }
        }
        else {
            if (!authCheckService.isAllowedToSeeCourseExercise(exercise, user)) {
                throw new AccessForbiddenException();
            }
        }

        RenderedProblemStatementDTO result = renderingService.render(exercise, selfContained);

        return ResponseEntity.ok().eTag("\"" + result.contentHash() + "\"").cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate()).body(result);
    }
}
