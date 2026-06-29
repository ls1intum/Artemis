package de.tum.cit.aet.artemis.atlas.service;

import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.MAX_DESCRIPTION_LENGTH;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.MAX_TITLE_LENGTH;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.appendAction;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.courseIdFromContext;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.errorJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.isBlank;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.missingCourseContextError;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.parseTaxonomyOrThrow;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.toJson;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.tryReserveWriteSlot;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.validateJustification;
import static de.tum.cit.aet.artemis.atlas.service.OrchestratorToolHelpers.writeQuotaError;

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
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyAtlasMLNotificationService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyService;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyValidationService;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;

/**
 * Write orchestrator tool that creates a new competency in the current course. Split from the former
 * monolithic orchestrator tools service so the create surface is registered as its own
 * {@link org.springframework.ai.tool.ToolCallbackProvider} bean.
 * <p>
 * The tool is course-scoped through the Spring AI {@link ToolContext}, shares the per-run write quota
 * with every other write tool via the {@link OrchestratorToolContextKeys.AppliedActionsBuffer}, and
 * appends a CREATE audit entry on success.
 */
@Lazy
@Service
@Conditional(AtlasEnabled.class)
public class CreatorToolsService {

    private static final Logger log = LoggerFactory.getLogger(CreatorToolsService.class);

    private final ObjectMapper objectMapper;

    private final CourseRepository courseRepository;

    private final CompetencyService competencyService;

    private final CompetencyValidationService competencyValidator;

    private final CompetencyAtlasMLNotificationService atlasMLNotificationService;

    /**
     * Creates the creator tools service.
     *
     * @param objectMapper               JSON serialiser for tool responses
     * @param courseRepository           repository for course lookups
     * @param competencyService          service persisting new competencies
     * @param competencyValidator        validator enforcing competency creation invariants
     * @param atlasMLNotificationService notifies the AtlasML service of competency changes
     */
    public CreatorToolsService(ObjectMapper objectMapper, CourseRepository courseRepository, CompetencyService competencyService, CompetencyValidationService competencyValidator,
            CompetencyAtlasMLNotificationService atlasMLNotificationService) {
        this.objectMapper = objectMapper;
        this.courseRepository = courseRepository;
        this.competencyService = competencyService;
        this.competencyValidator = competencyValidator;
        this.atlasMLNotificationService = atlasMLNotificationService;
    }

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
            return missingCourseContextError(objectMapper);
        }
        if (!tryReserveWriteSlot(toolContext)) {
            return writeQuotaError(objectMapper);
        }
        if (isBlank(title)) {
            return errorJson(objectMapper, "title is required.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            return errorJson(objectMapper, "title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return errorJson(objectMapper, "description must be at most " + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        String justificationError = validateJustification(objectMapper, justification);
        if (justificationError != null) {
            return justificationError;
        }
        CompetencyTaxonomy parsedTaxonomy;
        try {
            parsedTaxonomy = parseTaxonomyOrThrow(taxonomy);
        }
        catch (IllegalArgumentException ex) {
            return errorJson(objectMapper, ex.getMessage());
        }
        Optional<Course> courseOpt = courseRepository.findById(courseId);
        if (courseOpt.isEmpty()) {
            return errorJson(objectMapper, "Course " + courseId + " not found.");
        }
        Course course = courseOpt.get();
        Competency competency = new Competency(title.trim(), description == null ? "" : description.trim(), null, CourseCompetency.DEFAULT_MASTERY_THRESHOLD, parsedTaxonomy,
                false);
        try {
            competencyValidator.checkForCreation(competency);
        }
        catch (BadRequestAlertException ex) {
            return errorJson(objectMapper, ex.getMessage());
        }
        List<Competency> created;
        try {
            created = competencyService.createCompetencies(List.of(competency), course);
        }
        catch (DataAccessException ex) {
            log.warn("createCompetency failed for course {}: {}", courseId, ex.getMessage());
            return errorJson(objectMapper, "Failed to create competency.");
        }
        Competency persisted = created.get(0);
        String detail = "Created competency " + persisted.getTitle() + " (" + parsedTaxonomy.name() + ").";
        appendAction(toolContext, AppliedActionDTO.create(persisted.getId(), persisted.getTitle(), detail, justification.trim()));
        atlasMLNotificationService.notifyAtlasML(List.of(persisted), OperationTypeDTO.UPDATE, "orchestrator competency creation");
        return toJson(objectMapper, Map.of("id", persisted.getId(), "title", persisted.getTitle(), "taxonomy", parsedTaxonomy.name()));
    }
}
