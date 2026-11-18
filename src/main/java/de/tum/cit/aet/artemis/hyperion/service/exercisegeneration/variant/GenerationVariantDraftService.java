package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.dto.versioning.ProgrammingExerciseSnapshotDTO;
import de.tum.cit.aet.artemis.exercise.repository.PlagiarismDetectionConfigRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamAssignmentConfigRepository;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation.SourceSnapshot;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile.GenerationCapabilityService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismDetectionConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
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

    private final PlagiarismDetectionConfigRepository plagiarismConfigs;

    private final ProgrammingExerciseImportService imports;

    private final GenerationCapabilityService capabilities;

    private final GitService git;

    private final JsonMapper mapper;

    private final String defaultBranch;

    public GenerationVariantDraftService(ProgrammingExerciseRepository exercises, ProgrammingExerciseBuildConfigRepository buildConfigs, ProgrammingExerciseImportService imports,
            GenerationCapabilityService capabilities, TeamAssignmentConfigRepository teamConfigs, PlagiarismDetectionConfigRepository plagiarismConfigs, GitService git,
            JsonMapper mapper, @Value("${artemis.version-control.default-branch:main}") String defaultBranch) {
        this.exercises = exercises;
        this.buildConfigs = buildConfigs;
        this.imports = imports;
        this.capabilities = capabilities;
        this.teamConfigs = teamConfigs;
        this.plagiarismConfigs = plagiarismConfigs;
        this.git = git;
        this.mapper = mapper;
        this.defaultBranch = defaultBranch;
    }

    /**
     * Creates only the database draft, reserving its common authoring job within the same transaction.
     * The caller holds a short shared source reservation through this commit and a separate reservation around repository copying.
     *
     * @param sourceId protected source
     * @param request  transformation whose difficulty metadata is applied to the new draft
     * @param reserve  reserves the new destination without dispatching remote work
     * @param <T>      reserved start result
     * @return result after the destination transaction commits
     */
    public <T> T prepare(long sourceId, VariantGenerationRequestDTO request, Function<ProgrammingExercise, T> reserve) {
        return exercises.prepareAuthoringDraft(() -> prepareAndReserve(sourceId, request, reserve));
    }

    /**
     * Captures current source metadata and branch heads under the caller's shared source reservation.
     *
     * @param sourceId authorized source
     * @return immutable baseline for optimistic copy validation
     */
    public SourceSnapshot captureSource(long sourceId) {
        ProgrammingExercise source = loadSource(sourceId);
        var config = buildConfigs.getProgrammingExerciseBuildConfigElseThrow(sourceId);
        String branch = config.getBranch() == null || config.getBranch().isBlank() ? defaultBranch : config.getBranch();
        Map<RepositoryType, String> heads = new EnumMap<>(RepositoryType.class);
        for (var role : java.util.List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS)) {
            String head = git.getLastCommitHash(source.getRepositoryURI(role), branch);
            if (head == null) {
                throw new IllegalStateException("The variant source has no " + role + " branch head");
            }
            heads.put(role, head);
        }
        var commits = new ProgrammingExerciseSnapshotDTO.CommitHashesDTO(heads.get(RepositoryType.TEMPLATE), heads.get(RepositoryType.SOLUTION), heads.get(RepositoryType.TESTS),
                Map.of());
        var metadata = ExerciseSnapshotDTO.of(source, config, commits);
        var frozen = mapper.readValue(mapper.writeValueAsString(metadata), ExerciseSnapshotDTO.class);
        Long groupId = source.getExerciseGroup() == null ? null : source.getExerciseGroup().getId();
        return new SourceSnapshot(heads, frozen, source.getCourseViaExerciseGroupOrCourseMember().getId(), groupId);
    }

    /**
     * Rejects an intervening source edit rather than copying mixed versions.
     *
     * @param sourceId source being copied
     * @param expected baseline captured before draft creation
     */
    public void verifySource(long sourceId, SourceSnapshot expected) {
        if (!Objects.equals(expected, captureSource(sourceId))) {
            throw new ConflictException("The variant source changed during preparation. Please start again.", "exerciseVariantGeneration", "variantSourceChanged");
        }
    }

    private <T> T prepareAndReserve(long sourceId, VariantGenerationRequestDTO request, Function<ProgrammingExercise, T> reserve) {
        ProgrammingExercise source = loadSource(sourceId);
        capabilities.requireSupportedConfiguration(source);
        ProgrammingExercise destination = skeleton(source, request);
        ProgrammingExercise prepared = imports.prepareImport(source, buildConfigs.getProgrammingExerciseBuildConfigElseThrow(sourceId), destination, null);
        return reserve.apply(prepared);
    }

    /**
     * Copies the source into the reserved destination, before the isolated worker is started.
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
        plagiarismConfigs.attachTo(source);
        return source;
    }

    private ProgrammingExercise skeleton(ProgrammingExercise source, VariantGenerationRequestDTO request) {
        ProgrammingExercise draft = new ProgrammingExercise();
        draft.copyImportSettingsFrom(source);
        draft.setCourse(source.getCourseViaExerciseGroupOrCourseMember());
        // A variant is not automatically released by copying a source's past date.
        draft.setReleaseDate(ZonedDateTime.now(ZoneOffset.UTC).plusYears(1));
        String title = source.getTitle() == null ? "Exercise" : source.getTitle();
        draft.setTitle(title.substring(0, Math.min(title.length(), 240)) + " variant");
        draft.setShortName("Variant" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        draft.setMaxPoints(source.getMaxPoints());
        draft.setBonusPoints(source.getBonusPoints());
        draft.setMode(source.getMode());
        draft.setTeamAssignmentConfig(source.getTeamAssignmentConfig());
        if (source.getPlagiarismDetectionConfig() != null) {
            draft.setPlagiarismDetectionConfig(new PlagiarismDetectionConfig(source.getPlagiarismDetectionConfig()));
        }
        draft.setDifficulty(request.targetDifficulty() != null ? request.targetDifficulty() : source.getDifficulty());
        return draft;
    }
}
