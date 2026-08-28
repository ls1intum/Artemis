import { supportsHyperionExerciseGeneration } from 'app/hyperion/exercise-generation/hyperion-generation-support';
import { ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

describe('Hyperion generation support', () => {
    it.each([ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_GRADLE])('supports Java project type %s', (projectType) => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, projectType)).toBe(true);
    });

    it('keeps the legacy unspecified Java project type eligible', () => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, undefined)).toBe(true);
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, null)).toBe(true);
    });

    it('rejects unsupported languages and project types', () => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.PYTHON, ProjectType.PLAIN_GRADLE)).toBe(false);
        expect(supportsHyperionExerciseGeneration(undefined, ProjectType.PLAIN_MAVEN)).toBe(false);
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, ProjectType.FACT)).toBe(false);
    });
});
