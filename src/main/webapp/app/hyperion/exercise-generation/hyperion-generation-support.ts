import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * The sandbox verifies each exercise with its own build: Gradle for the two Gradle types and Maven for the two Maven types. The
 * dependency variants only differ in whether the sample template ships a third-party dependency. The server stays the authority.
 */
const SUPPORTED_PROJECT_TYPES: ReadonlySet<ProjectType> = new Set([ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN]);

export function supportsHyperionExerciseGeneration(language: ProgrammingLanguage | undefined, projectType: ProjectType | null | undefined): boolean {
    return language === ProgrammingLanguage.JAVA && !!projectType && SUPPORTED_PROJECT_TYPES.has(projectType);
}

/** Both authoring pages must use the same document protocol for the same exercise. */
export function usesCanonicalProblemStatementSync(exercise: ProgrammingExercise | undefined, generationEnabled: boolean): boolean {
    return (
        generationEnabled &&
        !!exercise?.id &&
        !!exercise.isAtLeastEditor &&
        !exercise.exerciseGroup &&
        supportsHyperionExerciseGeneration(exercise.programmingLanguage, exercise.projectType)
    );
}

/**
 * The first reason an exercise cannot be generated or adapted, in the order an instructor can act on them: what the
 * exercise is comes before what has happened to it. Each value is a suffix of {@link HYPERION_GENERATION_BLOCKER_KEY}.
 */
export type HyperionGenerationBlocker =
    | 'unsupportedLanguage'
    | 'unsupportedProjectType'
    | 'examExercise'
    | 'staticCodeAnalysis'
    | 'sequentialTestRuns'
    | 'auxiliaryRepositories'
    | 'released'
    | 'studentParticipations';

export const HYPERION_GENERATION_BLOCKER_KEY = 'artemisApp.hyperion.generation.blocker.';

/**
 * Client-side preflight only; the server rechecks permissions, participation state, and repository configuration.
 * An exercise without a release date is a draft still being authored, so only a release date that has passed counts
 * as released.
 *
 * @returns the blocker, or `undefined` when the exercise is an eligible draft
 */
export function hyperionGenerationBlocker(exercise: ProgrammingExercise, now: number): HyperionGenerationBlocker | undefined {
    if (exercise.programmingLanguage !== ProgrammingLanguage.JAVA) {
        return 'unsupportedLanguage';
    }
    if (!supportsHyperionExerciseGeneration(exercise.programmingLanguage, exercise.projectType)) {
        return 'unsupportedProjectType';
    }
    if (exercise.exerciseGroup) {
        return 'examExercise';
    }
    if (exercise.staticCodeAnalysisEnabled) {
        return 'staticCodeAnalysis';
    }
    if (exercise.buildConfig?.sequentialTestRuns) {
        return 'sequentialTestRuns';
    }
    if (exercise.auxiliaryRepositories?.length) {
        return 'auxiliaryRepositories';
    }
    if (exercise.releaseDate && exercise.releaseDate.valueOf() <= now) {
        return 'released';
    }
    if (exercise.studentParticipations?.length || exercise.numberOfParticipations) {
        return 'studentParticipations';
    }
    return undefined;
}

export function isHyperionGenerationDraft(exercise: ProgrammingExercise | undefined, now: number): boolean {
    return !!exercise && hyperionGenerationBlocker(exercise, now) === undefined;
}
