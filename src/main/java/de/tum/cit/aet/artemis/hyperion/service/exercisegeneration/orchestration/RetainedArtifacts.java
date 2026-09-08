package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationArtifactCompleteness;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationFileChangeDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Turns the candidate a terminal run produced into the bounded, read-only snapshot that is retained for inspection when the run was not saved.
 * <p>
 * The snapshot is held in cluster memory and handed back over the API, so every rule here fails toward retaining less: the caps apply in a deterministic order, a file that trips
 * the persistence secret-material policy is dropped rather than blocking the rest, and any drop downgrades the snapshot to {@link ExerciseGenerationArtifactCompleteness#PARTIAL}.
 */
final class RetainedArtifacts {

    private static final Logger log = LoggerFactory.getLogger(RetainedArtifacts.class);

    /** The instructor-facing repositories, in the order an instructor reads them. Agent scratch files are workspace mechanics, not exercise content, and are not retained. */
    private static final List<RepositoryType> RETAINED_REPOSITORIES = List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);

    static final int MAX_FILE_CHARS = 128_000;

    static final int MAX_FILES = 400;

    static final int MAX_TOTAL_CHARS = 2_000_000;

    /** Matches the bound the status API already applies to the retained SPEC.md. */
    static final int MAX_PROBLEM_STATEMENT_CHARS = 100_000;

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private RetainedArtifacts() {
    }

    /**
     * Builds the bounded snapshot of an unsaved candidate.
     *
     * @return the snapshot; {@link ExerciseGenerationRetainedArtifactsDTO#isEmpty()} when the run produced nothing worth retaining
     */
    static ExerciseGenerationRetainedArtifactsDTO of(String jobId, Map<RepositoryType, Map<String, String>> producedFiles, @Nullable String problemStatement,
            @Nullable String specDocument) {
        // Repository order, then path order, so the caps below always cut the same tail rather than an arbitrary subset.
        List<Map.Entry<String, String>> candidateFiles = RETAINED_REPOSITORIES.stream()
                .flatMap(repository -> producedFiles.getOrDefault(repository, Map.of()).entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
                        .map(entry -> Map.entry(repository.name().toLowerCase(Locale.ROOT) + "/" + entry.getKey(), entry.getValue() == null ? "" : entry.getValue())))
                .toList();
        List<ExerciseGenerationRetainedFileDTO> retained = new ArrayList<>();
        boolean dropped = false;
        int remainingChars = MAX_TOTAL_CHARS;
        for (Map.Entry<String, String> candidate : candidateFiles) {
            if (retained.size() >= MAX_FILES || remainingChars <= 0) {
                dropped = true;
                break;
            }
            if (!isSafe(candidate.getKey(), candidate.getValue())) {
                dropped = true;
                continue;
            }
            String content = candidate.getValue();
            int allowance = Math.min(MAX_FILE_CHARS, remainingChars);
            if (content.length() > allowance) {
                content = content.substring(0, allowance);
                dropped = true;
            }
            remainingChars -= content.length();
            int separator = candidate.getKey().indexOf('/');
            retained.add(new ExerciseGenerationRetainedFileDTO(candidate.getKey().substring(0, separator), candidate.getKey().substring(separator + 1), content));
        }
        String statement = truncate(problemStatement, MAX_PROBLEM_STATEMENT_CHARS);
        dropped = dropped || !java.util.Objects.equals(problemStatement, statement);
        return new ExerciseGenerationRetainedArtifactsDTO(jobId, dropped ? ExerciseGenerationArtifactCompleteness.PARTIAL : ExerciseGenerationArtifactCompleteness.COMPLETE,
                statement, specDocument, retained);
    }

    /** Applies one successful structured mutation to the bounded live candidate snapshot returned to the run owner. */
    static ExerciseGenerationRetainedArtifactsDTO withFileUpdate(String jobId, @Nullable ExerciseGenerationRetainedArtifactsDTO current, ExerciseGenerationFileChangeDTO change,
            @Nullable String content) {
        String problemStatement = current == null ? null : current.problemStatement();
        String specDocument = current == null ? null : current.specDocument();
        boolean deletion = ExerciseGenerationFileChangeDTO.ACTION_DELETE.equals(change.action());
        if ("problem-statement.md".equals(change.path())) {
            return copyWithDocuments(jobId, current, deletion ? null : content, specDocument);
        }
        if ("SPEC.md".equals(change.path())) {
            return copyWithDocuments(jobId, current, problemStatement, deletion ? null : content);
        }
        RepositoryType repository = switch (change.repo()) {
            case ExerciseGenerationFileChangeDTO.REPOSITORY_TEMPLATE -> RepositoryType.TEMPLATE;
            case ExerciseGenerationFileChangeDTO.REPOSITORY_SOLUTION -> RepositoryType.SOLUTION;
            case ExerciseGenerationFileChangeDTO.REPOSITORY_TESTS -> RepositoryType.TESTS;
            default -> null;
        };
        if (repository == null) {
            return current == null ? new ExerciseGenerationRetainedArtifactsDTO(jobId, ExerciseGenerationArtifactCompleteness.COMPLETE, null, null, List.of()) : current;
        }
        Map<RepositoryType, Map<String, String>> files = new java.util.EnumMap<>(RepositoryType.class);
        for (RepositoryType type : RETAINED_REPOSITORIES) {
            files.put(type, new java.util.HashMap<>());
        }
        if (current != null) {
            for (ExerciseGenerationRetainedFileDTO file : current.files()) {
                RepositoryType type = RepositoryType.valueOf(file.repo().toUpperCase(Locale.ROOT));
                files.get(type).put(file.path(), file.content());
            }
        }
        String prefix = change.repo() + "/";
        String path = change.path().startsWith(prefix) ? change.path().substring(prefix.length()) : change.path();
        if (deletion) {
            files.get(repository).remove(path);
        }
        else if (content != null) {
            files.get(repository).put(path, content);
        }
        return of(jobId, files, problemStatement, specDocument);
    }

    private static ExerciseGenerationRetainedArtifactsDTO copyWithDocuments(String jobId, @Nullable ExerciseGenerationRetainedArtifactsDTO current,
            @Nullable String problemStatement, @Nullable String specDocument) {
        Map<RepositoryType, Map<String, String>> files = new java.util.EnumMap<>(RepositoryType.class);
        if (current != null) {
            for (ExerciseGenerationRetainedFileDTO file : current.files()) {
                files.computeIfAbsent(RepositoryType.valueOf(file.repo().toUpperCase(Locale.ROOT)), ignored -> new java.util.HashMap<>()).put(file.path(), file.content());
            }
        }
        return of(jobId, files, problemStatement, specDocument);
    }

    /** A screen that cannot run is not a screen that passed, so an assessment failure counts as unsafe. */
    private static boolean isSafe(String logicalPath, String content) {
        try {
            HyperionSecretMaterialPolicy.Assessment assessment = SECRET_MATERIAL_POLICY.assess(logicalPath, content.getBytes(StandardCharsets.UTF_8),
                    HyperionSecretMaterialPolicy.Origin.PERSISTENCE);
            if (!assessment.isSafe()) {
                log.info("Withholding {} from the retained generation candidate: {}", assessment.safePath(), SECRET_MATERIAL_POLICY.blockedObservation(assessment));
            }
            return assessment.isSafe();
        }
        catch (RuntimeException e) {
            log.warn("Could not screen a generated file for secret material; withholding it from the retained candidate", e);
            return false;
        }
    }

    private static @Nullable String truncate(@Nullable String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }
}
