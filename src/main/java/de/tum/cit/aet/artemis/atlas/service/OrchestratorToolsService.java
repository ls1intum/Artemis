package de.tum.cit.aet.artemis.atlas.service;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyDetailDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyIndexResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.ExtractedContentDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Tools exposed to the Atlas orchestrator LLM. Mixes read and write operations:
 * <ul>
 * <li>{@link #listCompetencyIndex(ToolContext)}, {@link #getCompetencyDetails(Long, ToolContext)},
 * {@link #getExerciseContent(Long, ToolContext)} — let the model inspect course state.</li>
 * <li>{@link #createCompetency}, {@link #editCompetency}, {@link #assignExerciseToCompetency},
 * {@link #unassignExerciseFromCompetency}, {@link #deleteCompetency} — mutate state.</li>
 * </ul>
 * The current course id and the per-run applied-actions list are injected via Spring AI's
 * {@link ToolContext}. Tool-context parameters are stripped from the JSON schema exposed to the
 * LLM, so the model cannot forge the course id or the audit log.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class OrchestratorToolsService {

    private static final Logger log = LoggerFactory.getLogger(OrchestratorToolsService.class);

    /** Tool-context key carrying the current course id; package-private so only the orchestrator service can populate it. */
    static final String COURSE_ID_KEY = "courseId";

    /**
     * Tool-context key carrying the per-run {@link AppliedActionsBuffer}. The buffer wraps a
     * {@link java.util.Collections#synchronizedList synchronized list} so concurrent tool calls
     * (Spring AI's parallel-tool-call roadmap) cannot race on append. Package-private for the
     * same reason as {@link #COURSE_ID_KEY}.
     */
    static final String APPLIED_ACTIONS_KEY = "appliedActions";

    /**
     * Hard cap on the number of write tool calls per orchestrator run. Mirrors the limit declared
     * in the system prompt; enforced here so a hallucinating model cannot spend more than this
     * many writes regardless of what the prompt says. {@link AppliedActionsBuffer#actions size}
     * counts all five write tool types.
     */
    private static final int MAX_WRITE_CALLS = 8;

    /**
     * Allowed weight bands for {@link #assignExerciseToCompetency}. The system prompt forbids any
     * other value; enforced here too because LLMs occasionally output {@code 0.7} or
     * {@code 0.30000001}. Comparison uses a small epsilon to absorb float-formatting drift from
     * the model.
     */
    private static final Set<Double> ALLOWED_WEIGHTS = Set.of(1.0, 0.5, 0.3);

    /**
     * Match tolerance for {@link #ALLOWED_WEIGHTS}. The bands are at least 0.2 apart from one
     * another, so even a generous tolerance cannot blur two bands together. Sized to absorb
     * realistic LLM rounding (e.g. {@code 0.30000001}) without admitting actual drift like
     * {@code 0.7}.
     */
    private static final double WEIGHT_TOLERANCE = 1e-6;

    /**
     * Soft cap on the LLM-supplied justification text shown verbatim in the audit log. One full
     * sentence rarely exceeds this; longer values are almost always model hallucination or copy-
     * pasted prompt text and would bloat the per-action JSON returned to the UI.
     */
    private static final int MAX_JUSTIFICATION_LENGTH = 500;

    /**
     * Hard cap on competency title length for LLM-driven create/edit. Matches Hibernate's default
     * {@code varchar(255)} for unannotated String columns on {@code BaseCompetency.title}.
     * {@link CompetencyValidationService} does not enforce a length cap, so an LLM with prompt-
     * injected content would otherwise be able to persist arbitrarily long titles.
     */
    private static final int MAX_TITLE_LENGTH = 255;

    /**
     * Hard cap on competency description length for LLM-driven create/edit. The {@code description}
     * column is unbounded {@code text}; this cap prevents a hallucinating or prompt-injected model
     * from bloating the per-action JSON and the underlying row.
     */
    private static final int MAX_DESCRIPTION_LENGTH = 10_000;

    private final ObjectMapper objectMapper;

    private final CourseRepository courseRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ExerciseRepository exerciseRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final ContentExtractionService contentExtractionService;

    private final CompetencyService competencyService;

    private final CourseCompetencyService courseCompetencyService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final CompetencyValidationService competencyValidator;

    private final CompetencyAtlasMLNotificationService atlasMLNotificationService;

    public OrchestratorToolsService(ObjectMapper objectMapper, CourseRepository courseRepository, CourseCompetencyRepository courseCompetencyRepository,
            ExerciseRepository exerciseRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository, ContentExtractionService contentExtractionService,
            CompetencyService competencyService, CourseCompetencyService courseCompetencyService, Optional<CompetencyProgressApi> competencyProgressApi,
            CompetencyValidationService competencyValidator, CompetencyAtlasMLNotificationService atlasMLNotificationService) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.exerciseRepository = exerciseRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.contentExtractionService = contentExtractionService;
        this.competencyService = competencyService;
        this.courseCompetencyService = courseCompetencyService;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyValidator = competencyValidator;
        this.atlasMLNotificationService = atlasMLNotificationService;
    }

    /**
     * LLM tool: returns the competency index for the current course as JSON.
     *
     * @param toolContext carries the current course id
     * @return the JSON-serialized index
     */
    @Tool(description = "List the competency index for the current course. Returns two sections: (1) competencies — id, title, taxonomy, type (competency or prerequisite), "
            + "linked exercises (with title, exercise type, and the current link weight — 1.0 / 0.5 / 0.3) and linked lecture units (with name and lecture-unit type); "
            + "(2) unassignedExercises — exercises in the course that are currently not linked to any competency (id, title, type), which are prime candidates for closing coverage gaps. "
            + "The initial index is already provided in the system prompt; call this again after any CREATE / DELETE so subsequent actions reference up-to-date ids.")
    public String listCompetencyIndex(ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        return toJson(listCompetencyIndex(courseId));
    }

    /**
     * Builds the competency index so callers can seed the system prompt and save a tool-call round-trip.
     *
     * @param courseId the course
     * @return the index
     */
    public CompetencyIndexResponseDTO listCompetencyIndex(long courseId) {
        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(courseId);
        List<CompetencyIndexDTO> entries = competencies.stream().map(OrchestratorToolsService::toIndexEntry).sorted(Comparator.comparing(CompetencyIndexDTO::id)).toList();
        Set<Long> linkedExerciseIds = competencies.stream().flatMap(c -> c.getExerciseLinks().stream()).map(CompetencyExerciseLink::getExercise).map(Exercise::getId)
                .collect(Collectors.toSet());
        // findAllExercisesByCourseId already filters by `e.course.id`, so exam exercises are excluded.
        List<CompetencyIndexResponseDTO.UnassignedExerciseRefDTO> unassigned = exerciseRepository.findAllExercisesByCourseId(courseId).stream()
                .filter(exercise -> !linkedExerciseIds.contains(exercise.getId()))
                .map(exercise -> new CompetencyIndexResponseDTO.UnassignedExerciseRefDTO(exercise.getId(), exercise.getTitle(), exerciseType(exercise)))
                .sorted(Comparator.comparing(CompetencyIndexResponseDTO.UnassignedExerciseRefDTO::id)).toList();
        return new CompetencyIndexResponseDTO(entries, unassigned);
    }

    /**
     * LLM tool: returns full details for a single competency in the current course as JSON.
     *
     * @param competencyId id to inspect
     * @param toolContext  carries the current course id
     * @return the JSON-serialized details
     */
    @Tool(description = "Get the full details (description, soft due date, mastery threshold, optional flag, and linked exercises/lecture units with their ids and types; "
            + "each exercise ref also carries its current link weight — 1.0 / 0.5 / 0.3) for a single competency in the current course.")
    public String getCompetencyDetails(@ToolParam(description = "id of the competency to inspect") Long competencyId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (competencyId == null) {
            return errorJson("competencyId is required.");
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }
        return toJson(toDetail(competency));
    }

    /**
     * LLM tool: extracts learning-relevant content for an exercise in the current course as JSON.
     *
     * @param exerciseId  id to extract
     * @param toolContext carries the current course id
     * @return the JSON-serialized content
     */
    @Tool(description = "Extract the learning-relevant content (title, problem statement text, and metadata) for an exercise that belongs to the current course. "
            + "Only programming exercises are text-extractable; for quiz, text, modeling, or file-upload exercises this returns a stub with the title and type "
            + "(no problem statement) — judge their fit by title alone in that case, don't call this tool repeatedly for the same non-programming id.")
    public String getExerciseContent(@ToolParam(description = "id of the exercise whose content should be extracted") Long exerciseId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (exerciseId == null) {
            return errorJson("exerciseId is required.");
        }
        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return errorJson("Exercise not found: " + exerciseId);
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson("Exercise " + exerciseId + " does not belong to the current course.");
        }
        if (!(exercise instanceof ProgrammingExercise)) {
            String title = Objects.requireNonNullElse(exercise.getTitle(), "");
            return toJson(Map.of("id", exerciseId, "title", title, "type", exerciseType(exercise), "textExtractable", false, "note",
                    "Content extraction is only available for programming exercises. Use the title and type to decide fit."));
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            return toJson(extracted);
        }
        catch (RuntimeException ex) {
            // Generic message — raw exception text could leak Hibernate/SQL detail into the LLM's summary.
            log.warn("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage(), ex);
            return errorJson("Failed to extract content for exercise " + exerciseId + ".");
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Write tools
    // -----------------------------------------------------------------------------------------------

    /**
     * LLM tool: creates a new competency in the current course and appends a CREATE action to the run log.
     *
     * @param title         concise title for the competency
     * @param description   one-to-three sentence description
     * @param taxonomy      Bloom taxonomy level as a string
     * @param justification one-sentence reason this competency needs to exist (shown to the instructor in the audit log)
     * @param toolContext   Spring AI tool context carrying the current course id and applied-actions list
     * @return JSON with the new competency id and title, or a JSON error
     */
    @Tool(description = "Create a new competency in the current course. Returns the created competency id and title as JSON. "
            + "Taxonomy must be one of REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE. "
            + "Call listCompetencyIndex again afterwards so subsequent actions can reference the new id.")
    public String createCompetency(@ToolParam(description = "concise competency title") String title,
            @ToolParam(description = "one-to-three sentence description of what a student who masters this competency can do") String description,
            @ToolParam(description = "Bloom taxonomy level: REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, or CREATE") String taxonomy,
            @ToolParam(description = "one-sentence reason this competency needs to exist, referencing the exercise(s) it will cover; shown to the instructor in the audit log") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError();
        }
        if (isBlank(title)) {
            return errorJson("title is required.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return errorJson("title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return errorJson("description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        String justificationError = validateJustification(justification);
        if (justificationError != null) {
            return justificationError;
        }
        CompetencyTaxonomy parsedTaxonomy;
        try {
            parsedTaxonomy = parseTaxonomyOrThrow(taxonomy);
        }
        catch (IllegalArgumentException ex) {
            return errorJson(ex.getMessage());
        }
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return errorJson("Course " + courseId + " not found.");
        }
        Course course = courseOpt.get();
        Competency competency = new Competency(title.trim(), description == null ? "" : description.trim(), null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, parsedTaxonomy,
                false);
        try {
            competencyValidator.checkForCreation(competency);
        }
        catch (BadRequestAlertException ex) {
            return errorJson(ex.getMessage());
        }
        List<Competency> created;
        try {
            created = competencyService.createCompetencies(List.of(competency), course);
        }
        catch (DataAccessException ex) {
            log.warn("createCompetency failed for course {}: {}", courseId, ex.getMessage());
            return errorJson("Failed to create competency.");
        }
        Competency persisted = created.get(0);
        String detail = "Created competency " + persisted.getTitle() + " (" + parsedTaxonomy.name() + ").";
        appendAction(toolContext, AppliedActionDTO.create(persisted.getId(), persisted.getTitle(), detail, justification.trim()));
        atlasMLNotificationService.notifyAtlasML(List.of(persisted), OperationTypeDTO.UPDATE, "orchestrator competency creation");
        return toJson(Map.of("id", persisted.getId(), "title", persisted.getTitle(), "taxonomy", parsedTaxonomy.name()));
    }

    /**
     * LLM tool: updates selected fields of an existing competency and appends an EDIT action.
     *
     * @param competencyId  id of the competency to edit
     * @param title         new title (or null to keep the current value)
     * @param description   new description (or null to keep the current value)
     * @param taxonomy      new Bloom taxonomy level (or null to keep the current value)
     * @param justification one-sentence reason this edit is necessary (shown to the instructor in the audit log)
     * @param toolContext   Spring AI tool context
     * @return JSON with the updated id and list of changed fields, or a JSON error / noop
     */
    @Tool(description = "Update selected fields of an existing competency in the current course. Only the non-null arguments are applied; pass null for fields you want "
            + "to keep unchanged. Taxonomy, when provided, must be one of REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE.")
    public String editCompetency(@ToolParam(description = "id of the competency to edit") Long competencyId,
            @ToolParam(description = "new title (null to keep current)", required = false) String title,
            @ToolParam(description = "new description (null to keep current)", required = false) String description,
            @ToolParam(description = "new Bloom taxonomy level (null to keep current)", required = false) String taxonomy,
            @ToolParam(description = "one-sentence reason this edit is necessary and why it still fits every currently-linked exercise/lecture unit") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError();
        }
        if (competencyId == null) {
            return errorJson("competencyId is required.");
        }
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            return errorJson("title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return errorJson("description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        String justificationError = validateJustification(justification);
        if (justificationError != null) {
            return justificationError;
        }
        // Non-fetch-joining findById is intentional — editCompetency only mutates scalar columns
        // (title, description, taxonomy). Lazy collections (exerciseLinks, lectureUnitLinks) are
        // never read or written here, so there is no LazyInitializationException risk and no
        // merge() can wipe collections. Use the fetch-joining variant only when collections are
        // touched (see deleteCompetency / getCompetencyDetails).
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency existing = competencyOpt.get();
        if (!belongsToCourse(existing, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }
        List<String> changes = new ArrayList<>();
        if (!isBlank(title) && !title.trim().equals(existing.getTitle())) {
            existing.setTitle(title.trim());
            changes.add("title");
        }
        if (description != null) {
            String trimmed = description.trim();
            if (!trimmed.equals(existing.getDescription())) {
                existing.setDescription(trimmed);
                changes.add("description");
            }
        }
        if (!isBlank(taxonomy)) {
            CompetencyTaxonomy parsedTaxonomy;
            try {
                parsedTaxonomy = parseTaxonomyOrThrow(taxonomy);
            }
            catch (IllegalArgumentException ex) {
                return errorJson(ex.getMessage());
            }
            if (parsedTaxonomy != existing.getTaxonomy()) {
                existing.setTaxonomy(parsedTaxonomy);
                changes.add("taxonomy");
            }
        }
        if (changes.isEmpty()) {
            return toJson(Map.of("status", "noop", "message", "No fields changed for competency " + competencyId + "."));
        }
        try {
            competencyValidator.checkForUpdate(existing);
        }
        catch (BadRequestAlertException ex) {
            return errorJson(ex.getMessage());
        }
        CourseCompetency saved;
        // Direct repository.save instead of CourseCompetencyService.updateCourseCompetency on
        // purpose: the service's update path overwrites softDueDate, masteryThreshold and
        // isOptional from a "values" entity and would zero them out here (the orchestrator only
        // edits title / description / taxonomy). Neither path triggers progress recalc, so the
        // consistency cost is a code comment, not behavioural drift.
        try {
            saved = courseCompetencyRepository.save(existing);
        }
        catch (DataAccessException ex) {
            log.warn("editCompetency failed for competency {}: {}", competencyId, ex.getMessage());
            return errorJson("Failed to update competency.");
        }
        String detail = "Updated " + String.join(", ", changes) + " for competency " + saved.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.edit(saved.getId(), saved.getTitle(), detail, justification.trim()));
        if (saved instanceof Competency persisted) {
            atlasMLNotificationService.notifyAtlasML(List.of(persisted), OperationTypeDTO.UPDATE, "orchestrator competency update");
        }
        return toJson(Map.of("id", saved.getId(), "title", saved.getTitle(), "changed", changes));
    }

    /**
     * LLM tool: creates or updates a CompetencyExerciseLink and appends an ASSIGN action.
     *
     * @param competencyId  id of the competency to link to
     * @param exerciseId    id of the exercise to link
     * @param weight        link weight in (0.0, 1.0]; must be picked from the weight bands (1.0 / 0.5 / 0.3)
     * @param justification one-sentence evidence-test outcome explaining why the chosen band is honest
     * @param toolContext   Spring AI tool context
     * @return JSON status on success, or a JSON error / noop
     */
    @Tool(description = "Link an exercise to a competency in the current course. Creates a new CompetencyExerciseLink, or updates its weight if the link already exists. "
            + "Weight is the strength of the evidence and must be picked from the bands defined in the system prompt: 1.0 (stand-alone), 0.5 (partial), 0.3 (incidental). "
            + "If the evidence would be below 0.3, do not call this tool.")
    public String assignExerciseToCompetency(@ToolParam(description = "id of the competency to link to") Long competencyId,
            @ToolParam(description = "id of the exercise to link") Long exerciseId,
            @ToolParam(description = "evidence-strength weight: 1.0 (stand-alone evidence), 0.5 (partial), 0.3 (incidental). Must be > 0 and <= 1.0") Double weight,
            @ToolParam(description = "one-sentence outcome of the evidence test naming the chosen band (1.0 / 0.5 / 0.3) and why it is honest") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError();
        }
        if (competencyId == null || exerciseId == null) {
            return errorJson("competencyId and exerciseId are required.");
        }
        if (weight == null) {
            return errorJson("weight is required: pick 1.0 (stand-alone), 0.5 (partial), or 0.3 (incidental).");
        }
        Double matchedBand = matchAllowedBand(weight);
        if (matchedBand == null) {
            return errorJson("weight must be one of 1.0 (stand-alone), 0.5 (partial), or 0.3 (incidental). If the evidence is too weak to link, do not call this tool.");
        }
        String justificationError = validateJustification(justification);
        if (justificationError != null) {
            return justificationError;
        }
        double effectiveWeight = matchedBand;

        // See editCompetency: scalar-only mutation, no fetch-join needed.
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }

        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (EntityNotFoundException ex) {
            return errorJson("Exercise not found: " + exerciseId);
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson("Exercise " + exerciseId + " does not belong to the current course.");
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(exerciseId, competencyId).orElse(null);

        String detail;
        if (existingLink != null) {
            if (Double.compare(existingLink.getWeight(), effectiveWeight) == 0) {
                return toJson(Map.of("status", "noop", "message",
                        "Exercise " + exerciseId + " is already linked to competency " + competencyId + " with weight " + formatWeight(effectiveWeight) + "."));
            }
            existingLink.setWeight(effectiveWeight);
            competencyExerciseLinkRepository.save(existingLink);
            detail = "Updated weight to " + formatWeight(effectiveWeight) + " for exercise " + exercise.getTitle() + " on competency " + competency.getTitle() + ".";
        }
        else {
            CompetencyExerciseLink newLink = new CompetencyExerciseLink(competency, exercise, effectiveWeight);
            competencyExerciseLinkRepository.save(newLink);
            detail = "Linked exercise " + exercise.getTitle() + " to competency " + competency.getTitle() + " (weight " + formatWeight(effectiveWeight) + ").";
        }

        appendAction(toolContext, AppliedActionDTO.assign(competencyId, competency.getTitle(), exerciseId, effectiveWeight, detail, justification.trim()));
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        return toJson(Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId, "weight", effectiveWeight));
    }

    /**
     * LLM tool: removes the link between an exercise and a competency and appends an UNASSIGN action.
     *
     * @param competencyId  id of the competency
     * @param exerciseId    id of the exercise
     * @param justification one-sentence reason stating why the current link fails the evidence test and which competency is the better home
     * @param toolContext   Spring AI tool context
     * @return JSON status on success or noop, or a JSON error
     */
    @Tool(description = "Remove the link between an exercise and a competency in the current course. Only call this when BOTH (a) passing the exercise would not credibly "
            + "demonstrate mastery of this competency (the link fails the evidence test) AND (b) a clearly better-fitting competency exists (or is being created in this run). "
            + "Idempotent — returns a noop status if no such link exists.")
    public String unassignExerciseFromCompetency(@ToolParam(description = "id of the competency") Long competencyId, @ToolParam(description = "id of the exercise") Long exerciseId,
            @ToolParam(description = "one-sentence reason stating (a) why the current link fails the evidence test and (b) which competency is the better home") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError();
        }
        if (competencyId == null || exerciseId == null) {
            return errorJson("competencyId and exerciseId are required.");
        }
        String justificationError = validateJustification(justification);
        if (justificationError != null) {
            return justificationError;
        }
        // See editCompetency: scalar-only mutation, no fetch-join needed.
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(exerciseId, competencyId).orElse(null);
        if (existingLink == null) {
            return toJson(Map.of("status", "noop", "message", "Exercise " + exerciseId + " is not linked to competency " + competencyId + "."));
        }

        Exercise exercise = existingLink.getExercise();
        // Defense-in-depth: assignExerciseToCompetency already scopes both ends, but a stale or
        // out-of-band link could still exist; refuse to delete it here so an LLM cannot cross
        // course boundaries via a forged exerciseId.
        if (exercise == null || !exerciseBelongsToCourse(exercise, courseId)) {
            return errorJson("Exercise " + exerciseId + " does not belong to the current course.");
        }
        competencyExerciseLinkRepository.delete(existingLink);
        String exerciseTitle = exercise.getTitle() != null ? exercise.getTitle() : "exercise " + exerciseId;
        String detail = "Removed link between exercise " + exerciseTitle + " and competency " + competency.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.unassign(competencyId, competency.getTitle(), exerciseId, detail, justification.trim()));
        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        return toJson(Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId));
    }

    /**
     * LLM tool: deletes a competency from the current course and appends a DELETE action.
     *
     * @param competencyId  id of the competency to delete
     * @param justification one-sentence reason this competency is obsolete (shown to the instructor in the audit log)
     * @param toolContext   Spring AI tool context
     * @return JSON status on success, or a JSON error
     */
    @Tool(description = "Delete a competency from the current course. Cascades to competency relations, progress records, and exercise/lecture-unit links. "
            + "Use only when the competency is no longer needed after the current exercise change.")
    public String deleteCompetency(@ToolParam(description = "id of the competency to delete") Long competencyId,
            @ToolParam(description = "one-sentence reason this competency is obsolete — typically that its only linked exercise was deleted or moved") String justification,
            ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError();
        }
        if (competencyId == null) {
            return errorJson("competencyId is required.");
        }
        String justificationError = validateJustification(justification);
        if (justificationError != null) {
            return justificationError;
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson("Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson("Competency " + competencyId + " does not belong to the current course.");
        }

        String title = competency.getTitle();
        Course course = competency.getCourse();
        // Snapshot the entity for Atlas ML before the cascade wipes it; notification is sent only after the delete commits.
        Competency competencyForAtlasMl = null;
        if (competency instanceof Competency competencyToDelete) {
            competencyForAtlasMl = new Competency(competencyToDelete);
            competencyForAtlasMl.setId(competencyToDelete.getId());
        }
        try {
            // Cascades inside CourseCompetencyService: competency relations, progress records,
            // and all CompetencyExerciseLink / CompetencyLectureUnitLink rows for this competency.
            // Blast radius per run is bounded by MAX_WRITE_CALLS (each delete consumes one slot).
            courseCompetencyService.deleteCourseCompetency(competency, course);
        }
        catch (DataAccessException ex) {
            log.warn("deleteCompetency failed for competency {}: {}", competencyId, ex.getMessage());
            return errorJson("Failed to delete competency.");
        }
        // Append before the external Atlas ML notification — consistent with the other four
        // tools and ensures the audit DTO is in the buffer even if the downstream notify call
        // throws (the DB delete has already committed).
        appendAction(toolContext, AppliedActionDTO.delete(competencyId, title, "Deleted competency " + title + ".", justification.trim()));
        if (competencyForAtlasMl != null) {
            atlasMLNotificationService.notifyAtlasML(List.of(competencyForAtlasMl), OperationTypeDTO.DELETE, "orchestrator competency deletion");
        }
        return toJson(Map.of("status", "ok", "deletedId", competencyId));
    }

    // -----------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------

    private static CompetencyIndexDTO toIndexEntry(CourseCompetency competency) {
        List<CompetencyIndexDTO.ExerciseLinkRefDTO> exercises = competency.getExerciseLinks().stream()
                .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId()))
                .map(link -> new CompetencyIndexDTO.ExerciseLinkRefDTO(link.getExercise().getTitle(), exerciseType(link.getExercise()), link.getWeight())).toList();
        List<CompetencyIndexDTO.LectureUnitRefDTO> lectureUnits = competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit)
                .filter(lu -> lu.getName() != null).sorted(Comparator.comparing(lu -> lu.getName())).map(lu -> new CompetencyIndexDTO.LectureUnitRefDTO(lu.getName(), lu.getType()))
                .toList();
        return new CompetencyIndexDTO(competency.getId(), competency.getTitle(), competency.getTaxonomy(), competency.getType(), exercises, lectureUnits);
    }

    private static CompetencyDetailDTO toDetail(CourseCompetency competency) {
        List<CompetencyDetailDTO.ExerciseRefDTO> exercises = competency.getExerciseLinks().stream()
                .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId())).map(link -> {
                    Exercise exercise = link.getExercise();
                    return new CompetencyDetailDTO.ExerciseRefDTO(exercise.getId(), exercise.getTitle(), exercise.getType(), link.getWeight());
                }).toList();
        // Mirror toIndexEntry's null-name filter so both tools expose the same lecture-unit set.
        List<CompetencyDetailDTO.LectureUnitRefDTO> lectureUnits = competency.getLectureUnitLinks().stream().filter(link -> link.getLectureUnit().getName() != null)
                .sorted(Comparator.comparing((CompetencyLectureUnitLink link) -> link.getLectureUnit().getId()))
                .map(link -> new CompetencyDetailDTO.LectureUnitRefDTO(link.getLectureUnit().getId(), link.getLectureUnit().getName(), link.getLectureUnit().getType())).toList();
        return new CompetencyDetailDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getType(),
                competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.isOptional(), exercises, lectureUnits);
    }

    private static String exerciseType(Exercise exercise) {
        return exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName();
    }

    private static boolean belongsToCourse(CourseCompetency competency, long courseId) {
        return competency.getCourse() != null && Objects.equals(courseId, competency.getCourse().getId());
    }

    /** Defense-in-depth: rejects exam exercises so a tool call cannot walk the lazy exam.course chain outside a transaction. */
    private static boolean exerciseBelongsToCourse(Exercise exercise, long courseId) {
        if (exercise.isExamExercise()) {
            return false;
        }
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course != null && Objects.equals(courseId, course.getId());
    }

    /**
     * Match {@code weight} to one of the {@link #ALLOWED_WEIGHTS} bands within
     * {@link #WEIGHT_TOLERANCE}. Returns the canonical band value (so callers persist exactly
     * {@code 1.0} / {@code 0.5} / {@code 0.3}, not {@code 0.30000001}) or {@code null} when the
     * value falls outside every band.
     */
    @Nullable
    private static Double matchAllowedBand(double weight) {
        for (Double band : ALLOWED_WEIGHTS) {
            if (Math.abs(weight - band) < WEIGHT_TOLERANCE) {
                return band;
            }
        }
        return null;
    }

    private static CompetencyTaxonomy parseTaxonomyOrThrow(String taxonomy) {
        try {
            return CompetencyTaxonomy.valueOf(taxonomy.trim().toUpperCase(Locale.ROOT));
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("taxonomy must be one of REMEMBER, UNDERSTAND, APPLY, ANALYZE, EVALUATE, CREATE.");
        }
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * Validate the LLM-supplied audit-log justification. Returns {@code null} on success or a
     * pre-serialized JSON error to return to the model. Centralized so all five write tools
     * share the exact same wording / cap.
     */
    @Nullable
    private String validateJustification(@Nullable String justification) {
        if (isBlank(justification)) {
            return errorJson("justification is required.");
        }
        if (justification.length() > MAX_JUSTIFICATION_LENGTH) {
            return errorJson("justification must be at most " + MAX_JUSTIFICATION_LENGTH + " characters.");
        }
        return null;
    }

    private String writeQuotaError() {
        return errorJson("Write tool call cap (" + MAX_WRITE_CALLS + ") reached for this run; finalize and return.");
    }

    /**
     * Atomically reserves one slot against {@link #MAX_WRITE_CALLS} for this run, returning
     * {@code false} when the cap is already exhausted. Reservation happens before any persistence
     * or external notification so a hallucinating model cannot exceed the cap even under Spring
     * AI's roadmap parallel-tool-call execution — the previous separate {@code size()} pre-check
     * followed by {@code add()} was a TOCTOU race that could let concurrent tools both pass the
     * cap and exceed it.
     */
    private static boolean tryReserveWriteSlot(@Nullable ToolContext toolContext) {
        AppliedActionsBuffer buffer = appliedActionsBufferFromContext(toolContext);
        return buffer == null || buffer.tryReserveSlot(MAX_WRITE_CALLS);
    }

    private static String formatWeight(double weight) {
        return String.format(Locale.ROOT, "%.2f", weight);
    }

    private String missingCourseContextError() {
        return errorJson("No course context available for this tool call.");
    }

    private String errorJson(String message) {
        return toJson(Map.of("error", message));
    }

    private static @Nullable Long courseIdFromContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(COURSE_ID_KEY);
        return value instanceof Number number ? number.longValue() : null;
    }

    @Nullable
    private static AppliedActionsBuffer appliedActionsBufferFromContext(@Nullable ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(APPLIED_ACTIONS_KEY);
        return value instanceof AppliedActionsBuffer buffer ? buffer : null;
    }

    /**
     * Append an action to the per-run audit buffer. The list inside
     * {@link AppliedActionsBuffer} is a {@link java.util.Collections#synchronizedList synchronized
     * list}, so concurrent tool callbacks (Spring AI's parallel-tool-call support) cannot race.
     */
    private static void appendAction(ToolContext toolContext, AppliedActionDTO action) {
        AppliedActionsBuffer buffer = appliedActionsBufferFromContext(toolContext);
        if (buffer != null) {
            buffer.actions().add(action);
        }
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        }
        catch (JsonProcessingException e) {
            return "{\"error\": \"Failed to serialize response\"}";
        }
    }

    /**
     * Typed wrapper for the per-run applied-actions list passed through Spring AI's
     * {@link ToolContext}. The contained list MUST be a synchronized list — Spring AI's roadmap
     * includes parallel tool-call execution, and {@link #appendAction} is the only path that
     * mutates it. The {@code reservedSlots} counter enforces {@link #MAX_WRITE_CALLS} atomically
     * via {@link #tryReserveSlot(int)} so concurrent tool callbacks cannot both pass the cap and
     * exceed it (the old {@code size()}-then-{@code add()} pre-check was racy).
     */
    record AppliedActionsBuffer(List<AppliedActionDTO> actions, AtomicInteger reservedSlots) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        AppliedActionsBuffer(List<AppliedActionDTO> actions) {
            this(actions, new AtomicInteger());
        }

        boolean tryReserveSlot(int cap) {
            // reservedSlots is the only counter against the cap; it is incremented once per
            // attempted write (before persistence) and never decremented, so a hallucinating model
            // cannot retry past the cap by failing a mutation. A previous version also added
            // actions.size() to this check, which double-counted successful writes and halved the
            // effective cap.
            while (true) {
                int current = reservedSlots.get();
                if (current >= cap) {
                    return false;
                }
                if (reservedSlots.compareAndSet(current, current + 1)) {
                    return true;
                }
            }
        }
    }
}
