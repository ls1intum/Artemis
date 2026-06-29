package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.MAX_DESCRIPTION_LENGTH;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.MAX_TITLE_LENGTH;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.appendAction;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.belongsToCourse;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.courseIdFromContext;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.isBlank;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.missingCourseContextError;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.parseTaxonomyOrThrow;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.tryReserveWriteSlot;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.validateJustification;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.writeQuotaError;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.atlasml.SaveCompetencyRequestDTO.OperationTypeDTO;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CourseCompetencyService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Write orchestrator tools that mutate or remove an existing competency in the current course:
 * {@code editCompetency} updates scalar fields and {@code deleteCompetency} removes the competency
 * and its links. Split from the former monolithic orchestrator tools service so the edit/delete
 * surface is registered as its own {@link org.springframework.ai.tool.ToolCallbackProvider} bean.
 * <p>
 * Both tools are course-scoped through the Spring AI {@link ToolContext}, share the per-run write
 * quota with every other write tool via the {@link OrchestratorToolContextKeys.AppliedActionsBuffer},
 * and append an EDIT / DELETE audit entry on success. Edits and deletions are mirrored to AtlasML.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class EditorToolsService {

    private static final Logger log = LoggerFactory.getLogger(EditorToolsService.class);

    private final ObjectMapper objectMapper;

    private final CourseCompetencyRepository courseCompetencyRepository;

    private final CourseCompetencyService courseCompetencyService;

    private final CompetencyValidationService competencyValidator;

    private final CompetencyAtlasMLNotificationService atlasMLNotificationService;

    /**
     * Creates the editor tools service.
     *
     * @param objectMapper               JSON serialiser for tool responses
     * @param courseCompetencyRepository repository for competency lookups and scalar updates
     * @param courseCompetencyService    service performing the cascading delete
     * @param competencyValidator        validator enforcing competency update invariants
     * @param atlasMLNotificationService notifies the AtlasML service of competency changes
     */
    public EditorToolsService(ObjectMapper objectMapper, CourseCompetencyRepository courseCompetencyRepository, CourseCompetencyService courseCompetencyService,
            CompetencyValidationService competencyValidator, CompetencyAtlasMLNotificationService atlasMLNotificationService) {
        this.objectMapper = objectMapper;
        this.courseCompetencyRepository = courseCompetencyRepository;
        this.courseCompetencyService = courseCompetencyService;
        this.competencyValidator = competencyValidator;
        this.atlasMLNotificationService = atlasMLNotificationService;
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
            return missingCourseContextError(objectMapper);
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError(objectMapper);
        }
        if (competencyId == null) {
            return errorJson(objectMapper, "competencyId is required.");
        }
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            return errorJson(objectMapper, "title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return errorJson(objectMapper, "description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        String justificationError = validateJustification(objectMapper, justification);
        if (justificationError != null) {
            return justificationError;
        }
        // Non-fetch-joining findById is intentional — editCompetency only mutates scalar columns
        // (title, description, taxonomy). Lazy collections (exerciseLinks, lectureUnitLinks) are
        // never read or written here, so there is no LazyInitializationException risk and no
        // merge() can wipe collections. Use the fetch-joining variant only when collections are
        // touched (see deleteCompetency).
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findById(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson(objectMapper, "Competency not found: " + competencyId);
        }
        CourseCompetency existing = competencyOpt.get();
        if (!belongsToCourse(existing, courseId)) {
            return errorJson(objectMapper, "Competency " + competencyId + " does not belong to the current course.");
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
                return errorJson(objectMapper, ex.getMessage());
            }
            if (parsedTaxonomy != existing.getTaxonomy()) {
                existing.setTaxonomy(parsedTaxonomy);
                changes.add("taxonomy");
            }
        }
        if (changes.isEmpty()) {
            return toJson(objectMapper, Map.of("status", "noop", "message", "No fields changed for competency " + competencyId + "."));
        }
        try {
            competencyValidator.checkForUpdate(existing);
        }
        catch (BadRequestAlertException ex) {
            return errorJson(objectMapper, ex.getMessage());
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
            return errorJson(objectMapper, "Failed to update competency.");
        }
        String detail = "Updated " + String.join(", ", changes) + " for competency " + saved.getTitle() + ".";
        appendAction(toolContext, AppliedActionDTO.edit(saved.getId(), saved.getTitle(), detail, justification.trim()));
        if (saved instanceof Competency persisted) {
            atlasMLNotificationService.notifyAtlasML(List.of(persisted), OperationTypeDTO.UPDATE, "orchestrator competency update");
        }
        return toJson(objectMapper, Map.of("id", saved.getId(), "title", saved.getTitle(), "changed", changes));
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
            return missingCourseContextError(objectMapper);
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError(objectMapper);
        }
        if (competencyId == null) {
            return errorJson(objectMapper, "competencyId is required.");
        }
        String justificationError = validateJustification(objectMapper, justification);
        if (justificationError != null) {
            return justificationError;
        }
        Optional<CourseCompetency> competencyOpt = courseCompetencyRepository.findByIdWithExercisesAndLectureUnitsAndLectures(competencyId);
        if (competencyOpt.isEmpty()) {
            return errorJson(objectMapper, "Competency not found: " + competencyId);
        }
        CourseCompetency competency = competencyOpt.get();
        if (!belongsToCourse(competency, courseId)) {
            return errorJson(objectMapper, "Competency " + competencyId + " does not belong to the current course.");
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
            return errorJson(objectMapper, "Failed to delete competency.");
        }
        // Append before the external Atlas ML notification — consistent with the other write
        // tools and ensures the audit DTO is in the buffer even if the downstream notify call
        // throws (the DB delete has already committed).
        appendAction(toolContext, AppliedActionDTO.delete(competencyId, title, "Deleted competency " + title + ".", justification.trim()));
        if (competencyForAtlasMl != null) {
            atlasMLNotificationService.notifyAtlasML(List.of(competencyForAtlasMl), OperationTypeDTO.DELETE, "orchestrator competency deletion");
        }
        return toJson(objectMapper, Map.of("status", "ok", "deletedId", competencyId));
    }
}
