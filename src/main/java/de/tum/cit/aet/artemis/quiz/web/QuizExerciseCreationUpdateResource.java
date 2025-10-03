package de.tum.cit.aet.artemis.quiz.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.course.CourseService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.dto.exercise.QuizExerciseFromEditorDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;

/**
 * REST controller for creating and updating quiz exercises.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/quiz/")
public class QuizExerciseCreationUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseCreationUpdateResource.class);

    private static final String ENTITY_NAME = "quizExercise";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final QuizExerciseService quizExerciseService;

    private final CourseService courseService;

    private final AuthorizationCheckService authCheckService;

    private final ChannelService channelService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final QuizExerciseRepository quizExerciseRepository;

    public QuizExerciseCreationUpdateResource(QuizExerciseService quizExerciseService, QuizExerciseRepository quizExerciseRepository, CourseService courseService,
            AuthorizationCheckService authCheckService, ChannelService channelService, Optional<CompetencyProgressApi> competencyProgressApi) {
        this.quizExerciseService = quizExerciseService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.courseService = courseService;
        this.authCheckService = authCheckService;
        this.channelService = channelService;
        this.competencyProgressApi = competencyProgressApi;
    }

    /**
     * POST /quiz-exercises : Create a new quizExercise.
     *
     * @param quizExercise the quizExercise to create
     * @param files        the files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in {@code quizExercise}
     * @return the ResponseEntity with status 201 (Created) and with body the new quizExercise, or with status 400 (Bad Request) if the quizExercise has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "quiz-exercises", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditor
    public ResponseEntity<QuizExercise> createQuizExercise(@RequestPart("exercise") QuizExercise quizExercise,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) throws URISyntaxException, IOException {
        log.info("REST request to create QuizExercise : {}", quizExercise);
        if (quizExercise.getId() != null) {
            throw new BadRequestAlertException("A new quizExercise cannot already have an ID", ENTITY_NAME, "idExists");
        }

        // check if quiz is valid
        if (!quizExercise.isValid()) {
            // TODO: improve error message and tell the client why the quiz is invalid (also see below in update Quiz)
            throw new BadRequestAlertException("The quiz exercise is invalid", ENTITY_NAME, "invalidQuiz");
        }

        quizExercise.validateGeneralSettings();
        // Valid exercises have set either a course or an exerciseGroup
        quizExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);

        // Retrieve the course over the exerciseGroup or the given courseId
        Course course = courseService.retrieveCourseOverExerciseGroupOrCourseId(quizExercise);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        quizExerciseService.handleDndQuizFileCreation(quizExercise, files);

        QuizExercise result = quizExerciseService.save(quizExercise);

        channelService.createExerciseChannel(result, Optional.ofNullable(quizExercise.getChannelName()));

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(result));

        return ResponseEntity.created(new URI("/api/quiz/quiz-exercises/" + result.getId()))
                .headers(HeaderUtil.createEntityCreationAlert(applicationName, true, ENTITY_NAME, result.getId().toString())).body(result);
    }

    /**
     * PATCH /quiz-exercises/:exerciseId : Update an existing quizExercise with a DTO.
     *
     * @param exerciseId                the id of the quizExercise to save
     * @param quizExerciseFromEditorDTO the quizExercise to update
     * @param files                     the new files for drag and drop questions to upload (optional). The original file name must equal the file path of the image in
     *                                      {@code quizExercise}
     * @param notificationText          about the quiz exercise update that should be displayed to the student group
     * @return the ResponseEntity with status 200 (OK) and with body the updated quizExercise, or with status 400 (Bad Request) if the quizExercise is not valid, or with status 500
     *         (Internal Server Error) if the quizExercise couldn't be updated
     */
    @PatchMapping(value = "quiz-exercises/{exerciseId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<QuizExercise> updateQuizExercise(@PathVariable Long exerciseId, @RequestPart("exercise") QuizExerciseFromEditorDTO quizExerciseFromEditorDTO,
            @RequestPart(value = "files", required = false) List<MultipartFile> files, @RequestParam(value = "notificationText", required = false) String notificationText)
            throws IOException {
        log.info("REST request to patch quiz exercise : {}", exerciseId);
        QuizExercise quizBase = quizExerciseRepository.findByIdWithQuestionsAndStatisticsAndCompetenciesAndBatchesAndGradingCriteriaElseThrow(exerciseId);

        QuizExercise originalQuiz = quizExerciseService.copyFieldsForUpdate(quizBase);

        quizExerciseService.mergeDTOIntoDomainObject(quizBase, quizExerciseFromEditorDTO);
        QuizExercise result = quizExerciseService.performUpdate(originalQuiz, quizBase, files, notificationText);
        return ResponseEntity.ok(result);
    }
}
