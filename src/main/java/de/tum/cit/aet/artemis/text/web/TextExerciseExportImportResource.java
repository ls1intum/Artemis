package de.tum.cit.aet.artemis.text.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.athena.api.AthenaApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.util.ResponseUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.dto.SubmissionExportOptionsDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssignmentConfigDTO;
import de.tum.cit.aet.artemis.exercise.service.ExerciseVersionService;
import de.tum.cit.aet.artemis.lecture.dto.CompetencyLinkDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismDetectionConfigDTO;
import de.tum.cit.aet.artemis.text.config.TextEnabled;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.dto.ImportTextExerciseDTO;
import de.tum.cit.aet.artemis.text.dto.TextExerciseResponseDTO;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.service.TextExerciseImportService;
import de.tum.cit.aet.artemis.text.service.TextSubmissionExportService;

/**
 * REST controller for managing TextExercise.
 */
@Conditional(TextEnabled.class)
@Lazy
@RestController
@RequestMapping("api/text/")
public class TextExerciseExportImportResource {

    private static final Logger log = LoggerFactory.getLogger(TextExerciseExportImportResource.class);

    private static final String ENTITY_NAME = "textExercise";

    private final TextExerciseImportService textExerciseImportService;

    private final TextSubmissionExportService textSubmissionExportService;

    private final AuthorizationCheckService authCheckService;

    private final Optional<AthenaApi> athenaApi;

    private final TextExerciseRepository textExerciseRepository;

    private final UserRepository userRepository;

    private final ExerciseVersionService exerciseVersionService;

    private final CourseRepository courseRepository;

    private final ExerciseGroupRepository exerciseGroupRepository;

    public TextExerciseExportImportResource(TextExerciseRepository textExerciseRepository, UserRepository userRepository, AuthorizationCheckService authCheckService,
            TextExerciseImportService textExerciseImportService, TextSubmissionExportService textSubmissionExportService, Optional<AthenaApi> athenaApi,
            ExerciseVersionService exerciseVersionService, CourseRepository courseRepository, ExerciseGroupRepository exerciseGroupRepository) {
        this.textExerciseRepository = textExerciseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.textExerciseImportService = textExerciseImportService;
        this.textSubmissionExportService = textSubmissionExportService;
        this.athenaApi = athenaApi;
        this.exerciseVersionService = exerciseVersionService;
        this.courseRepository = courseRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
    }

    /**
     * POST /text-exercises/import: Imports an existing text exercise into an existing course
     * <p>
     * This will import the whole exercise except for the participations and dates. Referenced
     * entities will get cloned and assigned a new id.
     *
     * @param sourceExerciseIdQuery The ID of the original exercise which should get imported (provided as a query parameter; preferred)
     * @param sourceExerciseIdPath  The ID of the original exercise which should get imported (provided as a legacy path variable; deprecated)
     * @param importExerciseDTO     The new exercise containing values that should get overwritten in the
     *                                  imported exercise, s.a. the title or difficulty
     * @return The imported exercise (200), a not found error (404) if the template does not exist,
     *         or a forbidden error (403) if the user is not at least an instructor in the target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping({ "text-exercises/import", "text-exercises/import/{sourceExerciseId}" })
    @EnforceAtLeastEditor
    public ResponseEntity<TextExerciseResponseDTO> importExercise(@RequestParam(name = "sourceExerciseId", required = false) Long sourceExerciseIdQuery,
            @PathVariable(name = "sourceExerciseId", required = false) Long sourceExerciseIdPath, @RequestBody ImportTextExerciseDTO importExerciseDTO) throws URISyntaxException {
        long sourceExerciseId = sourceExerciseIdQuery != null ? sourceExerciseIdQuery : (sourceExerciseIdPath != null ? sourceExerciseIdPath : -1L);
        // Build a transient entity from the dumb DTO, attaching managed Course/ExerciseGroup loaded by id.
        final TextExercise importedExercise = toExercise(importExerciseDTO);
        if (sourceExerciseId <= 0 || (importedExercise.getCourseViaExerciseGroupOrCourseMember() == null && importedExercise.getExerciseGroup() == null)) {
            log.debug("Either the courseId or exerciseGroupId must be set for an import");
            throw new BadRequestAlertException("Either the courseId or exerciseGroupId must be set for an import", ENTITY_NAME, "noCourseIdOrExerciseGroupId");
        }
        importedExercise.checkCourseAndExerciseGroupExclusivity(ENTITY_NAME);
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var originalTextExercise = textExerciseRepository.findByIdWithExampleSubmissionsAndResultsElseThrow(sourceExerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, importedExercise, user);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, originalTextExercise, user);
        // validates general settings: points, dates
        importedExercise.validateGeneralSettings();

        // Athena: Check that only allowed athena modules are used, if not we catch the exception and disable feedback suggestions for the imported exercise
        // If Athena is disabled and the service is not present, we also disable feedback suggestions
        try {
            athenaApi.ifPresentOrElse(api -> api.checkHasAccessToAthenaModule(importedExercise, importedExercise.getCourseViaExerciseGroupOrCourseMember(), ENTITY_NAME),
                    () -> importedExercise.setFeedbackSuggestionModule(null));
        }
        catch (BadRequestAlertException e) {
            importedExercise.setFeedbackSuggestionModule(null);
        }

        final var newTextExercise = textExerciseImportService.importTextExercise(originalTextExercise, importedExercise);
        exerciseVersionService.createExerciseVersion(newTextExercise, user);
        return ResponseEntity.created(new URI("/api/text/text-exercises/" + newTextExercise.getId())).body(TextExerciseResponseDTO.of(newTextExercise));
    }

    /**
     * Builds a transient {@link TextExercise} from the import request DTO.
     * <p>
     * Only scalar/enum/date fields and nested config are set. The Course / ExerciseGroup referenced by id are loaded as
     * managed entities (so the subsequent role checks have access to the configured groups) and attached to the new
     * transient exercise. No entity graph from the request is persisted.
     *
     * @param dto the import payload
     * @return a transient TextExercise carrying the values to overwrite in the imported exercise
     */
    private TextExercise toExercise(ImportTextExerciseDTO dto) {
        if (dto == null) {
            throw new BadRequestAlertException("No text exercise was provided.", ENTITY_NAME, "isNull");
        }
        TextExercise exercise = new TextExercise();
        exercise.setId(dto.id());
        exercise.setTitle(dto.title());
        exercise.setChannelName(dto.channelName());
        exercise.setShortName(dto.shortName());
        exercise.setProblemStatement(dto.problemStatement());
        exercise.setCategories(dto.categories());
        exercise.setDifficulty(dto.difficulty());
        exercise.setMode(dto.mode());
        exercise.setMaxPoints(dto.maxPoints());
        exercise.setBonusPoints(dto.bonusPoints());
        exercise.setIncludedInOverallScore(dto.includedInOverallScore());
        if (dto.allowComplaintsForAutomaticAssessments() != null) {
            exercise.setAllowComplaintsForAutomaticAssessments(dto.allowComplaintsForAutomaticAssessments());
        }
        if (dto.allowFeedbackRequests() != null) {
            exercise.setAllowFeedbackRequests(dto.allowFeedbackRequests());
        }
        if (dto.presentationScoreEnabled() != null) {
            exercise.setPresentationScoreEnabled(dto.presentationScoreEnabled());
        }
        if (dto.secondCorrectionEnabled() != null) {
            exercise.setSecondCorrectionEnabled(dto.secondCorrectionEnabled());
        }
        exercise.setFeedbackSuggestionModule(dto.feedbackSuggestionModule());
        exercise.setGradingInstructions(dto.gradingInstructions());
        exercise.setReleaseDate(dto.releaseDate());
        exercise.setStartDate(dto.startDate());
        exercise.setDueDate(dto.dueDate());
        exercise.setAssessmentDueDate(dto.assessmentDueDate());
        exercise.setExampleSolutionPublicationDate(dto.exampleSolutionPublicationDate());
        exercise.setExampleSolution(dto.exampleSolution());

        if (dto.teamAssignmentConfig() != null) {
            exercise.setTeamAssignmentConfig(toTeamAssignmentConfig(dto.teamAssignmentConfig()));
        }
        if (dto.plagiarismDetectionConfig() != null) {
            exercise.setPlagiarismDetectionConfig(toPlagiarismDetectionConfig(dto.plagiarismDetectionConfig()));
        }

        // Grading criteria (with their structured grading instructions, needed for the copy tracker during import)
        if (dto.gradingCriteria() != null && !dto.gradingCriteria().isEmpty()) {
            Set<GradingCriterion> criteria = new HashSet<>();
            dto.gradingCriteria().forEach(gcDto -> {
                GradingCriterion criterion = gcDto.toEntity();
                criterion.setExercise(exercise);
                criteria.add(criterion);
            });
            exercise.setGradingCriteria(criteria);
        }

        // Competency links as new unmanaged objects referencing only the competency id
        if (dto.competencyLinks() != null && !dto.competencyLinks().isEmpty()) {
            Set<CompetencyExerciseLink> links = new HashSet<>();
            for (CompetencyLinkDTO linkDto : dto.competencyLinks()) {
                if (linkDto == null || linkDto.competency() == null) {
                    throw new BadRequestAlertException("Each competency link must include a competency.", ENTITY_NAME, "competencyIdMissing");
                }
                Competency competencyRef = new Competency();
                competencyRef.setId(linkDto.competency().id());
                links.add(new CompetencyExerciseLink(competencyRef, exercise, linkDto.weight()));
            }
            exercise.setCompetencyLinks(links);
        }

        // Attach a managed Course and/or ExerciseGroup so role checks see the configured groups. The exclusivity invariant
        // (exactly one of the two) is validated downstream by checkCourseAndExerciseGroupExclusivity.
        if (dto.courseId() != null) {
            Course course = courseRepository.findByIdElseThrow(dto.courseId());
            exercise.setCourse(course);
        }
        if (dto.exerciseGroupId() != null) {
            ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(dto.exerciseGroupId());
            exercise.setExerciseGroup(exerciseGroup);
        }
        return exercise;
    }

    private static TeamAssignmentConfig toTeamAssignmentConfig(TeamAssignmentConfigDTO dto) {
        TeamAssignmentConfig config = new TeamAssignmentConfig();
        config.setMinTeamSize(dto.minTeamSize());
        config.setMaxTeamSize(dto.maxTeamSize());
        return config;
    }

    private static PlagiarismDetectionConfig toPlagiarismDetectionConfig(PlagiarismDetectionConfigDTO dto) {
        PlagiarismDetectionConfig config = new PlagiarismDetectionConfig();
        config.setContinuousPlagiarismControlEnabled(dto.continuousPlagiarismControlEnabled());
        config.setContinuousPlagiarismControlPostDueDateChecksEnabled(dto.continuousPlagiarismControlPostDueDateChecksEnabled());
        config.setContinuousPlagiarismControlPlagiarismCaseStudentResponsePeriod(dto.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod());
        config.setSimilarityThreshold(dto.similarityThreshold());
        config.setMinimumScore(dto.minimumScore());
        config.setMinimumSize(dto.minimumSize());
        return config;
    }

    /**
     * POST /text-exercises/:exerciseId/export-submissions : sends exercise submissions as zip
     *
     * @param exerciseId              the id of the exercise to get the repos from
     * @param submissionExportOptions the options that should be used for the export
     * @return ResponseEntity with status
     */
    @PostMapping("text-exercises/{exerciseId}/export-submissions")
    @EnforceAtLeastTutor
    @FeatureToggle(Feature.Exports)
    public ResponseEntity<Resource> exportSubmissions(@PathVariable long exerciseId, @RequestBody SubmissionExportOptionsDTO submissionExportOptions) {
        TextExercise textExercise = textExerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, textExercise, null);

        // TAs are not allowed to download all participations
        if (submissionExportOptions.exportAllParticipants()) {
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, textExercise.getCourseViaExerciseGroupOrCourseMember(), null);
        }

        Path zipFilePath = textSubmissionExportService.exportStudentSubmissionsElseThrow(exerciseId, submissionExportOptions);
        return ResponseUtil.ok(zipFilePath);
    }
}
