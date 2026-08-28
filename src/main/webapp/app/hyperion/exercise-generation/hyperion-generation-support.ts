import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

const SUPPORTED_PROJECT_TYPES = new Set<ProjectType>([ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_GRADLE]);

export function supportsHyperionExerciseGeneration(language: ProgrammingLanguage | undefined, projectType: ProjectType | null | undefined): boolean {
    return language === ProgrammingLanguage.JAVA && (projectType == null || SUPPORTED_PROJECT_TYPES.has(projectType));
}
