package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseService;

/** Interprets the instructor brief and distinguishes a real statement from the client-seeded template README. */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationRequestService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final ResourceLoaderService resourceLoaderService;

    private final Map<String, Optional<String>> normalizedDefaultReadmes = new ConcurrentHashMap<>();

    public GenerationRequestService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Whether the exercise's problem statement is a real, instructor-authored specification the generation must honour. A non-empty statement is not sufficient evidence: the
     * client seeds every new exercise with {@code templates/<language>[/<projectType>]/readme}, so a blank create form still reaches the server carrying that sample exercise.
     * Accepting it as a specification would make the agent faithfully rebuild the sample and skip the SPEC stage.
     *
     * @param exercise the exercise whose statement is judged
     * @return {@code true} only for a non-trivial statement that does not match the exercise's default template readme
     */
    public boolean isAuthoritativeProblemStatement(ProgrammingExercise exercise) {
        String statement = exercise.getProblemStatement();
        if (!isNonTrivialProblemStatement(statement)) {
            return false;
        }
        return !normalizeStatement(statement).equals(defaultTemplateReadme(exercise).orElse(null));
    }

    private Optional<String> defaultTemplateReadme(ProgrammingExercise exercise) {
        if (exercise.getProgrammingLanguage() == null) {
            return Optional.empty();
        }
        String key = exercise.getProgrammingLanguage().name() + "/" + (exercise.getProjectType() == null ? "" : exercise.getProjectType().name());
        return normalizedDefaultReadmes.computeIfAbsent(key, ignored -> loadDefaultTemplateReadme(exercise));
    }

    private Optional<String> loadDefaultTemplateReadme(ProgrammingExercise exercise) {
        List<Path> candidates = new ArrayList<>();
        if (exercise.getProjectType() != null) {
            candidates.add(ProgrammingExerciseService.getProgrammingLanguageProjectTypePath(exercise.getProgrammingLanguage(), exercise.getProjectType()).resolve("readme"));
        }
        candidates.add(ProgrammingExerciseService.getProgrammingLanguageTemplatePath(exercise.getProgrammingLanguage()).resolve("readme"));
        for (Path candidate : candidates) {
            try {
                Resource resource = resourceLoaderService.getResource(candidate);
                if (resource != null && resource.exists()) {
                    try (var input = resource.getInputStream()) {
                        return Optional.of(normalizeStatement(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
                    }
                }
            }
            catch (IOException | RuntimeException e) {
                // Fall through to the next candidate: an unreadable template readme must never break prompt building.
            }
        }
        return Optional.empty();
    }

    /** Whitespace-insensitive, because the statement reaches the server over HTTP and its line endings need not match the classpath resource it is compared against. */
    private static String normalizeStatement(String statement) {
        return WHITESPACE.matcher(statement).replaceAll(" ").strip();
    }

    /** Minimum stripped length for a problem statement to be a candidate specification rather than an empty field or a short placeholder. */
    private static final int NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS = 40;

    public boolean isNonTrivialProblemStatement(@Nullable String problemStatement) {
        return problemStatement != null && problemStatement.strip().length() >= NON_TRIVIAL_PROBLEM_STATEMENT_MIN_CHARS;
    }

    /**
     * Resolves the instruction for a generation run. A generation brief is authoritative and may change the task entirely, so it outranks an existing statement on a different
     * topic; that statement remains the starting point wherever the brief is silent. Adaptation feedback is always a targeted revision. With no brief, the statement alone binds.
     *
     * @param request  the generation request holding the optional prompt
     * @param exercise the exercise being generated or adapted
     * @return the resolved instruction for the agent
     */
    public String resolvePrompt(ExerciseGenerationRequestDTO request, ProgrammingExercise exercise) {
        String brief = request.prompt() == null ? "" : request.prompt().strip();
        boolean hasSpec = isAuthoritativeProblemStatement(exercise);
        if (!brief.isBlank()) {
            if (request.mode() == GenerationMode.ADAPT) {
                return "Apply this feedback as a targeted revision of the existing exercise. Preserve every statement requirement and artifact where the feedback is silent, and "
                        + "change only the statement, solution, template, tests, and task bindings that the feedback requires: " + brief;
            }
            if (hasSpec) {
                return "problem-statement.md holds the exercise's current problem statement. Apply this instruction, authoritative for this run, which may refine that statement or "
                        + "change the task (topic, named types, requirements); where it is silent, keep the statement's intent and stated requirements, then build the solution, "
                        + "template, and tests to match the resulting statement and add the [task] bindings for the tests you write: " + brief;
            }
            return brief;
        }
        if (hasSpec) {
            return "An initial problem statement is already in problem-statement.md. Treat it as the authoritative specification and build the solution, template, and tests to match "
                    + "it, keeping its intent and every stated requirement; refine its wording and add the [task] bindings for the tests you write.";
        }
        return "Generate a complete, correct programming exercise: a reference solution that passes all tests, a template that compiles but fails the tests, and meaningful tests.";
    }

    /**
     * Exposed so the resource can both guard a run and serve the set to clients, rather than have them hardcode a second copy of it.
     *
     * @return the languages Hyperion offers for whole-exercise generation
     */
    public Set<ProgrammingLanguage> supportedGenerationLanguages() {
        return LanguageGenerationProfile.supportedLanguages();
    }

    public boolean isGenerationSupported(@Nullable ProgrammingExercise exercise) {
        return LanguageGenerationProfile.isSupported(exercise);
    }
}
