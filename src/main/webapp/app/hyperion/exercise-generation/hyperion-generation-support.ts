import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

export function supportsHyperionExerciseGeneration(language: ProgrammingLanguage | undefined, projectType: ProjectType | null | undefined): boolean {
    return language === ProgrammingLanguage.JAVA && projectType === ProjectType.GRADLE_GRADLE;
}

/** Client-side preflight only; the server rechecks permissions, participation state, and repository configuration. */
export function isHyperionGenerationDraft(exercise: ProgrammingExercise | undefined, now: number): boolean {
    return (
        !!exercise &&
        supportsHyperionExerciseGeneration(exercise.programmingLanguage, exercise.projectType) &&
        exercise.releaseDate !== undefined &&
        exercise.releaseDate.valueOf() > now &&
        !exercise.exerciseGroup &&
        !exercise.staticCodeAnalysisEnabled &&
        !exercise.buildConfig?.sequentialTestRuns &&
        !exercise.auxiliaryRepositories?.length &&
        !exercise.studentParticipations?.length &&
        !exercise.numberOfParticipations
    );
}
