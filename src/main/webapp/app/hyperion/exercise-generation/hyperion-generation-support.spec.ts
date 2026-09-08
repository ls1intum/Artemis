import dayjs from 'dayjs/esm';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { isHyperionGenerationDraft, supportsHyperionExerciseGeneration } from 'app/hyperion/exercise-generation/hyperion-generation-support';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

describe('Hyperion generation support', () => {
    it('supports the qualified Java Gradle project type', () => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, ProjectType.GRADLE_GRADLE)).toBe(true);
    });

    it.each([ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.PLAIN_GRADLE, ProjectType.FACT, undefined, null])(
        'rejects unsupported or unspecified project type %s',
        (projectType) => {
            expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, projectType)).toBe(false);
        },
    );

    it('rejects non-Java or unspecified languages', () => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.PYTHON, ProjectType.GRADLE_GRADLE)).toBe(false);
        expect(supportsHyperionExerciseGeneration(undefined, ProjectType.GRADLE_GRADLE)).toBe(false);
    });
});

describe('Hyperion draft eligibility', () => {
    const now = Date.UTC(2026, 8, 8);
    function draft(): ProgrammingExercise {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.projectType = ProjectType.GRADLE_GRADLE;
        exercise.releaseDate = dayjs(now).add(1, 'day');
        return exercise;
    }

    it('accepts an unreleased, unmodified Java Gradle draft', () => {
        expect(isHyperionGenerationDraft(draft(), now)).toBe(true);
    });

    it('rejects missing exercises, release dates, and exercises released at the current instant', () => {
        expect(isHyperionGenerationDraft(undefined, now)).toBe(false);
        const exercise = draft();
        exercise.releaseDate = undefined;
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
        exercise.releaseDate = dayjs(now);
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
    });

    it('rejects static analysis, sequential tests, auxiliary repositories, and student participations', () => {
        const exercise = draft();
        exercise.staticCodeAnalysisEnabled = true;
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
        exercise.staticCodeAnalysisEnabled = false;
        exercise.buildConfig = new ProgrammingExerciseBuildConfig();
        exercise.buildConfig.sequentialTestRuns = true;
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
        exercise.buildConfig.sequentialTestRuns = false;
        exercise.auxiliaryRepositories = [new AuxiliaryRepository()];
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
        exercise.auxiliaryRepositories = [];
        exercise.studentParticipations = [new StudentParticipation()];
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
    });
});
