import dayjs from 'dayjs/esm';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { AuxiliaryRepository } from 'app/programming/shared/entities/programming-exercise-auxiliary-repository-model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { hyperionGenerationBlocker, isHyperionGenerationDraft, supportsHyperionExerciseGeneration } from 'app/hyperion/exercise-generation/hyperion-generation-support';
import { ProgrammingExercise, ProgrammingLanguage, ProjectType } from 'app/programming/shared/entities/programming-exercise.model';

describe('Hyperion generation support', () => {
    it.each([ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_GRADLE])('supports the Java Gradle project type %s', (projectType) => {
        expect(supportsHyperionExerciseGeneration(ProgrammingLanguage.JAVA, projectType)).toBe(true);
    });

    it.each([ProjectType.MAVEN_MAVEN, ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_BLACKBOX, ProjectType.FACT, undefined, null])(
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
        exercise.projectType = ProjectType.PLAIN_GRADLE;
        exercise.releaseDate = dayjs(now).add(1, 'day');
        return exercise;
    }

    it('accepts an unreleased, unmodified Java Gradle draft', () => {
        expect(hyperionGenerationBlocker(draft(), now)).toBeUndefined();
        expect(isHyperionGenerationDraft(draft(), now)).toBe(true);
        expect(isHyperionGenerationDraft(undefined, now)).toBe(false);
    });

    it('treats a missing release date as a draft and the current instant as released', () => {
        const exercise = draft();
        exercise.releaseDate = undefined;
        expect(hyperionGenerationBlocker(exercise, now)).toBeUndefined();
        exercise.releaseDate = dayjs(now);
        expect(hyperionGenerationBlocker(exercise, now)).toBe('released');
        expect(isHyperionGenerationDraft(exercise, now)).toBe(false);
    });

    it('names the first blocker in an order the instructor can act on', () => {
        const exercise = draft();
        exercise.programmingLanguage = ProgrammingLanguage.PYTHON;
        exercise.projectType = ProjectType.PLAIN_MAVEN;
        exercise.staticCodeAnalysisEnabled = true;
        expect(hyperionGenerationBlocker(exercise, now)).toBe('unsupportedLanguage');
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        expect(hyperionGenerationBlocker(exercise, now)).toBe('unsupportedProjectType');
        exercise.projectType = ProjectType.GRADLE_GRADLE;
        exercise.exerciseGroup = new ExerciseGroup();
        expect(hyperionGenerationBlocker(exercise, now)).toBe('examExercise');
        exercise.exerciseGroup = undefined;
        expect(hyperionGenerationBlocker(exercise, now)).toBe('staticCodeAnalysis');
        exercise.staticCodeAnalysisEnabled = false;
        exercise.buildConfig = new ProgrammingExerciseBuildConfig();
        exercise.buildConfig.sequentialTestRuns = true;
        expect(hyperionGenerationBlocker(exercise, now)).toBe('sequentialTestRuns');
        exercise.buildConfig.sequentialTestRuns = false;
        exercise.auxiliaryRepositories = [new AuxiliaryRepository()];
        expect(hyperionGenerationBlocker(exercise, now)).toBe('auxiliaryRepositories');
        exercise.auxiliaryRepositories = [];
        exercise.studentParticipations = [new StudentParticipation()];
        expect(hyperionGenerationBlocker(exercise, now)).toBe('studentParticipations');
        exercise.studentParticipations = [];
        exercise.numberOfParticipations = 3;
        expect(hyperionGenerationBlocker(exercise, now)).toBe('studentParticipations');
    });
});
