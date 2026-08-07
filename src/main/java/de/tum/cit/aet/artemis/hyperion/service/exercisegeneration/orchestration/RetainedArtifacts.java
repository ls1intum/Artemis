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
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedArtifactsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRetainedFileDTO;
import de.tum.cit.aet.artemis.hyperion.service.HyperionSecretMaterialPolicy;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Turns the candidate a terminal run produced into the bounded, read-only snapshot that is retained for inspection when the run was not saved.
 * <p>
 * Three rules make this safe to hold in cluster memory and safe to hand back over the API, and all three fail toward retaining less rather than more:
 * <ul>
 * <li><b>Bounded.</b> Per-file, file-count, and total-size caps apply in a deterministic order, so the same candidate always yields the same snapshot and a pathological run
 * cannot grow the retained map without limit.</li>
 * <li><b>Screened.</b> Every file goes through the same secret-material policy that guards persistence. A file that trips it is dropped, not exported and not blocked — one bad
 * file must not cost the instructor the other forty.</li>
 * <li><b>Honest.</b> Any drop, for any reason, downgrades the snapshot to {@link ExerciseGenerationArtifactCompleteness#PARTIAL}, so a reader is never told a truncated
 * candidate is the whole one.</li>
 * </ul>
 */
final class RetainedArtifacts {

    private static final Logger log = LoggerFactory.getLogger(RetainedArtifacts.class);

    /**
     * Only the three instructor-facing repositories are retained, in the order an instructor reads them. The agent's scratch files are deliberately excluded: they are
     * workspace mechanics, not exercise content, and the run's file-change index already records that they existed.
     */
    private static final List<RepositoryType> RETAINED_REPOSITORIES = List.of(RepositoryType.TEMPLATE, RepositoryType.SOLUTION, RepositoryType.TESTS);

    /** Large enough for any generated source file; small enough that one runaway file cannot dominate the budget below. */
    static final int MAX_FILE_CHARS = 128_000;

    /** Comfortably above a generated exercise's file count (tens), so the cap only ever fires on a pathological run. */
    static final int MAX_FILES = 400;

    /** The whole snapshot's budget. Sized for several generated exercises' worth of source, and the reason a per-file cap alone is not enough. */
    static final int MAX_TOTAL_CHARS = 2_000_000;

    /** Bounds the retained statement the same way the status API already bounds the retained SPEC.md. */
    static final int MAX_PROBLEM_STATEMENT_CHARS = 100_000;

    private static final HyperionSecretMaterialPolicy SECRET_MATERIAL_POLICY = new HyperionSecretMaterialPolicy();

    private RetainedArtifacts() {
    }

    /**
     * Builds the bounded snapshot of an unsaved candidate.
     *
     * @param jobId            the run that produced the candidate
     * @param producedFiles    the produced files per repository, as captured from the sandbox
     * @param problemStatement the produced problem statement, if any
     * @param specDocument     the run's {@code SPEC.md}, if any
     * @return the snapshot; {@link ExerciseGenerationRetainedArtifactsDTO#isEmpty()} when the run produced nothing worth retaining
     */
    static ExerciseGenerationRetainedArtifactsDTO of(String jobId, Map<RepositoryType, Map<String, String>> producedFiles, @Nullable String problemStatement,
            @Nullable String specDocument) {
        // One flat, deterministically ordered pass: repository order first, then path order, so the same candidate always yields the same snapshot and the caps below always
        // cut the same tail rather than an arbitrary subset.
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

    /**
     * Applies the persistence secret-material policy to one candidate file. Assessment failures are treated as unsafe: a screen that cannot run is not a screen that passed.
     */
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
