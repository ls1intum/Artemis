package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseImportService;

/** Imports the source once, with the destination protected before it becomes visible to other requests. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationVariantDraftService {

    private final ProgrammingExerciseRepository exercises;

    private final ProgrammingExerciseBuildConfigRepository buildConfigs;

    private final TeamAssignmentConfigRepository teamConfigs;

    private final ProgrammingExerciseImportService imports;

    private final GenerationCapabilityService capabilities;

    public GenerationVariantDraftService(ProgrammingExerciseRepository exercises, ProgrammingExerciseBuildConfigRepository buildConfigs, ProgrammingExerciseImportService imports,
            GenerationCapabilityService capabilities, TeamAssignmentConfigRepository teamConfigs) {
        this.exercises = exercises;
        this.buildConfigs = buildConfigs;
        this.imports = imports;
        this.capabilities = capabilities;
        this.teamConfigs = teamConfigs;
    }

    /**
     * Creates only the database draft, reserving its common authoring job within the same transaction.
     * A caller-held source reservation excludes edits until repository copying finishes.
     *
     * @param sourceId protected source
     * @param request  transformation whose difficulty metadata is applied to the new draft
     * @param reserve  reserves the new destination without dispatching remote work
     * @param <T>      reserved start result
     * @return result after the destination transaction commits
     */
    public <T> T prepare(long sourceId, VariantGenerationRequestDTO request, Function<ProgrammingExercise, T> reserve) {
        return exercises.prepareAuthoringDraft(() -> {
            ProgrammingExercise source = exercises.findWithAllParticipationsById(sourceId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise", sourceId));
            if (source.isExamExercise()) {
                capabilities.requireMutable(source);
            }
            return prepareAndReserve(sourceId, request, reserve);
        });
    }

    private <T> T prepareAndReserve(long sourceId, VariantGenerationRequestDTO request, Function<ProgrammingExercise, T> reserve) {
        ProgrammingExercise source = loadSource(sourceId);
        capabilities.requireSupportedConfiguration(source);
        ProgrammingExercise destination = skeleton(source, request);
        ProgrammingExercise prepared = imports.prepareImport(source, buildConfigs.getProgrammingExerciseBuildConfigElseThrow(sourceId), destination, null);
        return reserve.apply(prepared);
    }

    /**
     * Copies the protected source into the reserved destination, before the isolated worker is started.
     *
     * @param sourceId      protected source
     * @param destinationId reserved destination
     */
    public void complete(long sourceId, long destinationId) {
        ProgrammingExercise source = loadSource(sourceId);
        ProgrammingExercise destination = exercises.findForCreationById(destinationId).orElseThrow();
        imports.completeImport(source, destination, true, false);
    }

    private ProgrammingExercise loadSource(long sourceId) {
        ProgrammingExercise source = exercises.findForAuthoringImportById(sourceId).orElseThrow(() -> new EntityNotFoundException("Programming Exercise", sourceId));
        teamConfigs.attachTo(source);
        return source;
    }

    private ProgrammingExercise skeleton(ProgrammingExercise source, VariantGenerationRequestDTO request) {
        ProgrammingExercise draft = new ProgrammingExercise();
        draft.copyImportSettingsFrom(source);
        if (source.isExamExercise()) {
            draft.setExerciseGroup(source.getExerciseGroup());
        }
        else {
            draft.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
            // Like a newly generated draft, a variant is not automatically released by copying a source's past date.
            draft.setReleaseDate(ZonedDateTime.now().plusYears(1));
        }
        String title = source.getTitle() == null ? "Exercise" : source.getTitle();
        draft.setTitle(title.substring(0, Math.min(title.length(), 240)) + " variant");
        draft.setShortName("Variant" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        draft.setMaxPoints(source.getMaxPoints());
        draft.setBonusPoints(source.getBonusPoints());
        draft.setMode(source.getMode());
        draft.setTeamAssignmentConfig(source.getTeamAssignmentConfig());
        draft.setDifficulty(request.targetDifficulty() != null ? request.targetDifficulty() : source.getDifficulty());
        return draft;
    }
}
