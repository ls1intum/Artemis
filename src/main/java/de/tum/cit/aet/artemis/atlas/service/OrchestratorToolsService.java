package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
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

    public static final String COURSE_ID_KEY = "courseId";

    public static final String APPLIED_ACTIONS_KEY = "appliedActions";

    private final ObjectMapper objectMapper;

    private final CourseRepository courseRepository;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final ExerciseRepository exerciseRepository;

    private final CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    private final ContentExtractionService contentExtractionService;

    private final CompetencyService competencyService;

    private final CourseCompetencyService courseCompetencyService;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public OrchestratorToolsService(ObjectMapper objectMapper, CourseRepository courseRepository, CourseCompetencyRepository courseCompetencyRepository,
            ExerciseRepository exerciseRepository, CompetencyExerciseLinkRepository competencyExerciseLinkRepository, ContentExtractionService contentExtractionService,
            CompetencyService competencyService, CourseCompetencyService courseCompetencyService, Optional<CompetencyProgressApi> competencyProgressApi) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.exerciseRepository = exerciseRepository;
        this.competencyExerciseLinkRepository = competencyExerciseLinkRepository;
        this.contentExtractionService = contentExtractionService;
        this.competencyService = competencyService;
        this.courseCompetencyService = courseCompetencyService;
        this.competencyProgressApi = competencyProgressApi;
    }

    // -----------------------------------------------------------------------------------------------
    // Read tools
    // -----------------------------------------------------------------------------------------------

    /**
     * LLM tool: returns the competency index for the current course as JSON.
     *
     * @param toolContext Spring AI tool context carrying the current course id
     * @return JSON-serialized competency index, or a JSON error when course context is missing
     */
    @Tool(description = "List the competency index for the current course. Returns two sections: (1) competencies — id, title, taxonomy, type (competency or prerequisite), "
            + "linked exercises (with title and the current link weight — 1.0 / 0.5 / 0.3) and linked lecture-unit names; (2) unassignedExercises — exercises in the course "
            + "that are currently not linked to any competency (id, title, type), which are prime candidates for closing coverage gaps. The initial index is already "
            + "provided in the system prompt; call this again after any CREATE / DELETE so subsequent actions reference up-to-date ids.")
    public String listCompetencyIndex(ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        return toJson(listCompetencyIndex(courseId));
    }

    /**
     * Builds the competency index for the given course so callers can seed the system prompt with
     * the initial state (saves a tool-call round-trip for the LLM).
     *
     * @param courseId the course whose competencies to fetch
     * @return wrapper with one entry per competency plus the list of course exercises currently
     *         linked to no competency
     */
    public CompetencyIndexResponseDTO listCompetencyIndex(long courseId) {
        Set<CourseCompetency> competencies = courseCompetencyRepository.findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(courseId);
        List<CompetencyIndexDTO> entries = competencies.stream().map(OrchestratorToolsService::toIndexEntry).sorted(Comparator.comparing(CompetencyIndexDTO::id)).toList();
        Set<Long> linkedExerciseIds = new HashSet<>();
        for (CourseCompetency competency : competencies) {
            if (competency.getExerciseLinks() == null) {
                continue;
            }
            for (CompetencyExerciseLink link : competency.getExerciseLinks()) {
                Exercise exercise = link.getExercise();
                if (exercise != null && exercise.getId() != null) {
                    linkedExerciseIds.add(exercise.getId());
                }
            }
        }
        List<CompetencyIndexResponseDTO.UnassignedExerciseRef> unassigned = exerciseRepository.findAllExercisesByCourseId(courseId).stream()
                .filter(exercise -> exercise.getId() != null && !linkedExerciseIds.contains(exercise.getId()))
                .map(exercise -> new CompetencyIndexResponseDTO.UnassignedExerciseRef(exercise.getId(), exercise.getTitle(),
                        exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName()))
                .sorted(Comparator.comparing(CompetencyIndexResponseDTO.UnassignedExerciseRef::id)).toList();
        return new CompetencyIndexResponseDTO(entries, unassigned);
    }

    /**
     * LLM tool: returns the full details for a single competency in the current course as JSON.
     *
     * @param competencyId id of the competency to inspect
     * @param toolContext  Spring AI tool context carrying the current course id
     * @return JSON-serialized competency detail, or a JSON error when inputs or access fail
     */
    @Tool(description = "Get the full details (description, soft due date, mastery threshold, optional flag, and linked exercises/lecture units with their ids and types; "
            + "each exercise ref also carries its current link weight — 1.0 / 0.5 / 0.3) for a single competency in the current course.")
    public String getCompetencyDetails(@ToolParam(description = "id of the competency to inspect") Long competencyId, ToolContext toolContext) {
        Long courseId = courseIdFromContext(toolContext);
        if (courseId == null) {
            return missingCourseContextError();
        }
        if (competencyId == null) {
            return toJson(Map.of("error", "competencyId is required."));
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }
        return toJson(toDetail(competency));
    }

    /**
     * LLM tool: extracts the learning-relevant content for an exercise in the current course as JSON.
     *
     * @param exerciseId  id of the exercise whose content should be extracted
     * @param toolContext Spring AI tool context carrying the current course id
     * @return JSON-serialized extracted content, or a JSON error when inputs or access fail
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
            return toJson(Map.of("error", "exerciseId is required."));
        }
        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (Exception ex) {
            return toJson(Map.of("error", "Exercise not found: " + exerciseId));
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return toJson(Map.of("error", "Exercise " + exerciseId + " does not belong to the current course."));
        }
        if (!(exercise instanceof ProgrammingExercise)) {
            String type = exercise.getType() != null ? exercise.getType() : exercise.getClass().getSimpleName();
            String title = exercise.getTitle() != null ? exercise.getTitle() : "";
            return toJson(Map.of("id", exerciseId, "title", title, "type", type, "textExtractable", false, "note",
                    "Content extraction is only available for programming exercises. Use the title and type to decide fit."));
        }
        try {
            ExtractedContentDTO extracted = contentExtractionService.extractContent(exercise);
            return toJson(extracted);
        }
        catch (Exception ex) {
            log.debug("getExerciseContent failed for exercise {}: {}", exerciseId, ex.getMessage());
            return toJson(Map.of("error", ex.getMessage() == null ? "Failed to extract content." : ex.getMessage()));
        }
    }

    // -----------------------------------------------------------------------------------------------
    // Write tools
    // -----------------------------------------------------------------------------------------------

    /**
     * LLM tool: creates a new competency in the current course and appends a CREATE action to the run log.
     *
     * @param title       concise title for the competency
     * @param description one-to-three sentence description
     * @param taxonomy    Bloom taxonomy level as a string
     * @param toolContext Spring AI tool context carrying the current course id and applied-actions list
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
        if (isBlank(title)) {
            return toJson(Map.of("error", "title is required."));
        }
        if (isBlank(justification)) {
            return toJson(Map.of("error", "justification is required."));
        }
        CompetencyTaxonomy parsedTaxonomy;
        try {
            parsedTaxonomy = parseTaxonomyOrThrow(taxonomy);
        }
        catch (IllegalArgumentException ex) {
            return toJson(Map.of("error", ex.getMessage()));
        }
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return toJson(Map.of("error", "Course " + courseId + " not found."));
        }
        Course course = courseOpt.get();
        Competency competency = new Competency(title.trim(), description == null ? "" : description.trim(), null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, parsedTaxonomy,
                false);
        List<Competency> created;
        try {
            created = competencyService.createCompetencies(List.of(competency), course);
        }
        catch (Exception ex) {
            log.warn("createCompetency failed for course {}: {}", courseId, ex.getMessage());
            return toJson(Map.of("error", "Failed to create competency: " + safeMessage(ex)));
        }
        if (created.isEmpty()) {
            return toJson(Map.of("error", "Failed to create competency: no competency returned."));
        }
        Competency persisted = created.get(0);
        String detail = "Created competency " + persisted.getTitle() + " (" + parsedTaxonomy.name() + ").";
        appendAction(toolContext, AppliedActionDTO.create(persisted.getId(), persisted.getTitle(), detail, justification.trim()));
        return toJson(Map.of("id", persisted.getId(), "title", persisted.getTitle(), "taxonomy", parsedTaxonomy.name()));
    }

    /**
     * LLM tool: updates selected fields of an existing competency and appends an EDIT action.
     *
     * @param competencyId id of the competency to edit
     * @param title        new title (or null to keep the current value)
     * @param description  new description (or null to keep the current value)
     * @param taxonomy     new Bloom taxonomy level (or null to keep the current value)
     * @param toolContext  Spring AI tool context
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
        if (competencyId == null) {
            return toJson(Map.of("error", "competencyId is required."));
        }
        if (isBlank(justification)) {
            return toJson(Map.of("error", "justification is required."));
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency existing = competencyOpt.get();
        if (!belongsToCourse(existing, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }
        List<String> changes = new ArrayList<>();
        if (!isBlank(title) && !title.trim().equals(existing.getTitle())) {
            existing.setTitle(title.trim());
            changes.add("title");
        }
        if (description != null && !description.equals(existing.getDescription())) {
            existing.setDescription(description);
            changes.add("description");
        }
        if (!isBlank(taxonomy)) {
            CompetencyTaxonomy parsedTaxonomy;
            try {
                parsedTaxonomy = parseTaxonomyOrThrow(taxonomy);
            }
            catch (IllegalArgumentException ex) {
                return toJson(Map.of("error", ex.getMessage()));
            }
            if (parsedTaxonomy != existing.getTaxonomy()) {
                existing.setTaxonomy(parsedTaxonomy);
                changes.add("taxonomy");
            }
        }
        if (changes.isEmpty()) {
            return toJson(Map.of("status", "noop", "message", "No fields changed for competency " + competencyId + "."));
        }
        CourseCompetency saved;
        try {
            saved = courseCompetencyRepository.save(existing);
        }
        catch (Exception ex) {
            log.warn("editCompetency failed for competency {}: {}", competencyId, ex.getMessage());
            return toJson(Map.of("error", "Failed to update competency: " + safeMessage(ex)));
        }
        String detail = "Updated " + String.join(", ", changes) + " for competency " + saved.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.edit(saved.getId(), saved.getTitle(), detail, justification.trim()));
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
        if (competencyId == null || exerciseId == null) {
            return toJson(Map.of("error", "competencyId and exerciseId are required."));
        }
        if (weight == null) {
            return toJson(Map.of("error", "weight is required: pick 1.0 (stand-alone), 0.5 (partial), or 0.3 (incidental)."));
        }
        if (weight <= 0.0 || weight > 1.0) {
            return toJson(Map.of("error", "weight must be greater than 0.0 and at most 1.0. If the evidence is too weak to link, do not call this tool."));
        }
        if (isBlank(justification)) {
            return toJson(Map.of("error", "justification is required."));
        }
        double effectiveWeight = weight;

        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }

        Exercise exercise;
        try {
            exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        }
        catch (Exception ex) {
            return toJson(Map.of("error", "Exercise not found: " + exerciseId));
        }
        if (!exerciseBelongsToCourse(exercise, courseId)) {
            return toJson(Map.of("error", "Exercise " + exerciseId + " does not belong to the current course."));
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId).stream()
                .filter(link -> link.getCompetency() != null && competencyId.equals(link.getCompetency().getId())).findFirst().orElse(null);

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

        competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        appendAction(toolContext, AppliedActionDTO.assign(competencyId, competency.getTitle(), exerciseId, effectiveWeight, detail, justification.trim()));
        return toJson(Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId, "weight", effectiveWeight));
    }

    /**
     * LLM tool: removes the link between an exercise and a competency and appends an UNASSIGN action.
     *
     * @param competencyId id of the competency
     * @param exerciseId   id of the exercise
     * @param toolContext  Spring AI tool context
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
        if (competencyId == null || exerciseId == null) {
            return toJson(Map.of("error", "competencyId and exerciseId are required."));
        }
        if (isBlank(justification)) {
            return toJson(Map.of("error", "justification is required."));
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }

        CompetencyExerciseLink existingLink = competencyExerciseLinkRepository.findByExerciseIdWithCompetency(exerciseId).stream()
                .filter(link -> link.getCompetency() != null && competencyId.equals(link.getCompetency().getId())).findFirst().orElse(null);
        if (existingLink == null) {
            return toJson(Map.of("status", "noop", "message", "Exercise " + exerciseId + " is not linked to competency " + competencyId + "."));
        }

        Exercise exercise = existingLink.getExercise();
        competencyExerciseLinkRepository.delete(existingLink);
        if (exercise != null) {
            competencyProgressApi.ifPresent(api -> api.updateProgressByLearningObjectAsync(exercise));
        }
        String exerciseTitle = exercise != null && exercise.getTitle() != null ? exercise.getTitle() : "exercise " + exerciseId;
        String detail = "Removed link between exercise " + exerciseTitle + " and competency " + competency.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.unassign(competencyId, competency.getTitle(), exerciseId, detail, justification.trim()));
        return toJson(Map.of("status", "ok", "competencyId", competencyId, "exerciseId", exerciseId));
    }

    /**
     * LLM tool: deletes a competency from the current course and appends a DELETE action.
     *
     * @param competencyId id of the competency to delete
     * @param toolContext  Spring AI tool context
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
        if (competencyId == null) {
            return toJson(Map.of("error", "competencyId is required."));
        }
        if (isBlank(justification)) {
            return toJson(Map.of("error", "justification is required."));
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return toJson(Map.of("error", "Competency not found: " + competencyId));
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return toJson(Map.of("error", "Competency " + competencyId + " does not belong to the current course."));
        }

        String title = competency.getTitle();
        Course course = competency.getCourse();
        try {
            courseCompetencyService.deleteCourseCompetency(competency, course);
        }
        catch (Exception ex) {
            log.warn("deleteCompetency failed for competency {}: {}", competencyId, ex.getMessage());
            return toJson(Map.of("error", "Failed to delete competency: " + safeMessage(ex)));
        }
        appendAction(toolContext, AppliedActionDTO.delete(competencyId, title, "Deleted competency " + title + ".", justification.trim()));
        return toJson(Map.of("status", "ok", "deletedId", competencyId));
    }

    // -----------------------------------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------------------------------

    private static CompetencyIndexDTO toIndexEntry(CourseCompetency competency) {
        List<CompetencyIndexDTO.ExerciseLinkRef> exercises = new ArrayList<>();
        if (competency.getExerciseLinks() != null) {
            competency.getExerciseLinks().stream().filter(link -> link.getExercise() != null && link.getExercise().getId() != null && link.getExercise().getTitle() != null)
                    .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId()))
                    .forEach(link -> exercises.add(new CompetencyIndexDTO.ExerciseLinkRef(link.getExercise().getTitle(), link.getWeight())));
        }
        List<String> lectureUnitNames = competency.getLectureUnitLinks() == null ? List.of()
                : competency.getLectureUnitLinks().stream().map(CompetencyLectureUnitLink::getLectureUnit).filter(lu -> lu != null).map(lu -> lu.getName()).filter(n -> n != null)
                        .sorted().toList();
        return new CompetencyIndexDTO(competency.getId(), competency.getTitle(), competency.getTaxonomy(), competency.getType(), exercises, lectureUnitNames);
    }

    private static CompetencyDetailDTO toDetail(CourseCompetency competency) {
        List<CompetencyDetailDTO.ExerciseRef> exercises = new ArrayList<>();
        if (competency.getExerciseLinks() != null) {
            competency.getExerciseLinks().stream().filter(link -> link.getExercise() != null && link.getExercise().getId() != null)
                    .sorted(Comparator.comparing((CompetencyExerciseLink link) -> link.getExercise().getId())).forEach(link -> {
                        Exercise exercise = link.getExercise();
                        exercises.add(new CompetencyDetailDTO.ExerciseRef(exercise.getId(), exercise.getTitle(), exercise.getType(), link.getWeight()));
                    });
        }
        List<CompetencyDetailDTO.LectureUnitRef> lectureUnits = new ArrayList<>();
        if (competency.getLectureUnitLinks() != null) {
            competency.getLectureUnitLinks().stream().filter(link -> link.getLectureUnit() != null && link.getLectureUnit().getId() != null)
                    .sorted(Comparator.comparing((CompetencyLectureUnitLink link) -> link.getLectureUnit().getId())).forEach(link -> lectureUnits
                            .add(new CompetencyDetailDTO.LectureUnitRef(link.getLectureUnit().getId(), link.getLectureUnit().getName(), link.getLectureUnit().getType())));
        }
        return new CompetencyDetailDTO(competency.getId(), competency.getTitle(), competency.getDescription(), competency.getTaxonomy(), competency.getType(),
                competency.getSoftDueDate(), competency.getMasteryThreshold(), competency.isOptional(), exercises, lectureUnits);
    }

    private static boolean belongsToCourse(CourseCompetency competency, long courseId) {
        return competency.getCourse() != null && courseId == competency.getCourse().getId();
    }

    private static boolean exerciseBelongsToCourse(Exercise exercise, long courseId) {
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return course != null && courseId == course.getId();
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

    private static String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String formatWeight(double weight) {
        return String.format(Locale.ROOT, "%.2f", weight);
    }

    private String missingCourseContextError() {
        return toJson(Map.of("error", "No course context available for this tool call."));
    }

    private static @Nullable Long courseIdFromContext(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object value = toolContext.getContext().get(COURSE_ID_KEY);
        return value instanceof Long longValue ? longValue : null;
    }

    private static void appendAction(ToolContext toolContext, AppliedActionDTO action) {
        if (toolContext == null || toolContext.getContext() == null) {
            return;
        }
        Object value = toolContext.getContext().get(APPLIED_ACTIONS_KEY);
        if (value instanceof List<?> rawList) {
            @SuppressWarnings("unchecked")
            List<AppliedActionDTO> list = (List<AppliedActionDTO>) rawList;
            list.add(action);
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
}
