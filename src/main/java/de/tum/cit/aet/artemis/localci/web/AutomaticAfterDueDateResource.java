package de.tum.cit.aet.artemis.programming.web.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.exam.api.ExamAccessApi;
import de.tum.cit.aet.artemis.exam.api.ExamRepositoryApi;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.dto.AutomaticAfterDueDatePreviewRequestDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.localci.AutomaticAfterDueDateService;

@Profile(PROFILE_LOCALCI)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class AutomaticAfterDueDateResource {

    private static final String ENTITY_NAME = "programmingExercise";

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final Optional<ExamRepositoryApi> examRepositoryApi;

    private final AutomaticAfterDueDateService automaticAfterDueDateService;

    private final Optional<ExamAccessApi> examAccessApi;

    public AutomaticAfterDueDateResource(final ProgrammingExerciseRepository programmingExerciseRepository, final AuthorizationCheckService authorizationCheckService,
            final Optional<ExamRepositoryApi> examRepositoryApi, final AutomaticAfterDueDateService automaticAfterDueDateService, final Optional<ExamAccessApi> examAccessApi) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.examRepositoryApi = examRepositoryApi;
        this.automaticAfterDueDateService = automaticAfterDueDateService;
        this.examAccessApi = examAccessApi;
    }

    /**
     * POST /programming-exercises/timeline/automatic-after-due-date-preview : Preview the LocalCI-computed "Run Tests after Due Date" value.
     *
     * @param requestDTO contains exercise/exam context and timeline/build-phase inputs for computation
     * @return the computed date or null if the value would not be set
     */
    @PostMapping("programming-exercises/timeline/automatic-after-due-date-preview")
    @EnforceAtLeastEditor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ZonedDateTime> previewAutomaticAfterDueDate(@RequestBody AutomaticAfterDueDatePreviewRequestDTO requestDTO) throws IOException {
        Long programmingExerciseId = requestDTO.programmingExerciseId();
        Long examId = requestDTO.examId();

        ProgrammingExercise programmingExercise = null;
        if (programmingExerciseId != null) {
            programmingExercise = programmingExerciseRepository.findByIdWithBuildConfigElseThrow(programmingExerciseId);
            authorizationCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, programmingExercise, null);
        }
        Exam exam = null;
        if (examId != null) {
            exam = examRepositoryApi.orElseThrow(() -> new BadRequestAlertException("Exam support is not enabled", ENTITY_NAME, "examSupportNotEnabled")).findByIdElseThrow(examId);
            examAccessApi.orElseThrow().checkCourseAndExamAccessForEditorElseThrow(exam.getCourse().getId(), exam.getId());
        }

        ZonedDateTime previewDate = automaticAfterDueDateService.getAutomaticBuildAndTestDate(requestDTO, programmingExercise, exam);
        return ResponseEntity.ok(previewDate);
    }

}
