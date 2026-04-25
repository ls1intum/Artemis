import dayjs from 'dayjs/esm';
import * as dateUtils from 'app/shared/util/date.utils';
import { toUpdateProgrammingExerciseDTO } from 'app/programming/manage/services/update-programming-exercise-dto.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';

describe('UpdateProgrammingExerciseDTO mapping', () => {
    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should convert a programming exercise to an update DTO with all fields', () => {
        jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue('2024-06-01T10:00:00.000Z' as any);
        jest.spyOn(ExerciseService, 'stringifyExerciseDTOCategories').mockReturnValue(['cat1']);

        const course = new Course();
        course.id = 1;

        const buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.sequentialTestRuns = true;
        buildConfig.buildScript = 'mvn test';
        buildConfig.checkoutSolutionRepository = true;
        buildConfig.timeoutSeconds = 300;
        buildConfig.allowBranching = true;
        buildConfig.branchRegex = 'main|develop';

        const exercise = new ProgrammingExercise(course, undefined);
        exercise.id = 42;
        exercise.title = 'Test Exercise';
        exercise.shortName = 'TE';
        exercise.maxPoints = 100;
        exercise.bonusPoints = 10;
        exercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.packageName = 'de.test';
        exercise.showTestNamesToStudents = true;
        exercise.allowOnlineEditor = true;
        exercise.allowOfflineIde = true;
        exercise.allowOnlineIde = false;
        exercise.releaseTestsWithExampleSolution = true;
        exercise.releaseDate = dayjs('2024-05-01');
        exercise.dueDate = dayjs('2024-06-01');
        exercise.buildConfig = buildConfig;
        exercise.gradingCriteria = [
            {
                id: 1,
                title: 'Criterion 1',
                structuredGradingInstructions: [
                    {
                        id: 10,
                        credits: 5,
                        gradingScale: 'good',
                        instructionDescription: 'Well done',
                        feedback: 'Great',
                        usageCount: 1,
                    },
                ],
            } as any,
        ];
        exercise.auxiliaryRepositories = [
            {
                id: 1,
                name: 'aux-repo',
                checkoutDirectory: '/aux',
                repositoryUri: 'https://example.com/aux.git',
                description: 'Helper repo',
            } as any,
        ];

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.id).toBe(42);
        expect(dto.title).toBe('Test Exercise');
        expect(dto.shortName).toBe('TE');
        expect(dto.maxPoints).toBe(100);
        expect(dto.bonusPoints).toBe(10);
        expect(dto.programmingLanguage).toBe(ProgrammingLanguage.JAVA);
        expect(dto.packageName).toBe('de.test');
        expect(dto.showTestNamesToStudents).toBeTrue();
        expect(dto.allowOnlineEditor).toBeTrue();
        expect(dto.allowOnlineIde).toBeFalse();
        expect(dto.releaseTestsWithExampleSolution).toBeTrue();
        expect(dto.courseId).toBe(1);
        expect(dto.exerciseGroupId).toBeUndefined();
        expect(dto.categories).toEqual(['cat1']);

        // Build config
        expect(dto.buildConfig).toBeDefined();
        expect(dto.buildConfig!.sequentialTestRuns).toBeTrue();
        expect(dto.buildConfig!.buildScript).toBe('mvn test');
        expect(dto.buildConfig!.checkoutSolutionRepository).toBeTrue();
        expect(dto.buildConfig!.timeoutSeconds).toBe(300);
        expect(dto.buildConfig!.allowBranching).toBeTrue();

        // Grading criteria
        expect(dto.gradingCriteria).toHaveLength(1);
        expect(dto.gradingCriteria![0].title).toBe('Criterion 1');
        expect(dto.gradingCriteria![0].structuredGradingInstructions).toHaveLength(1);

        // Auxiliary repos
        expect(dto.auxiliaryRepositories).toHaveLength(1);
        expect(dto.auxiliaryRepositories![0].name).toBe('aux-repo');
    });

    it('should handle exercise without build config', () => {
        jest.spyOn(dateUtils, 'convertDateFromClient').mockReturnValue(undefined);
        jest.spyOn(ExerciseService, 'stringifyExerciseDTOCategories').mockReturnValue(undefined as any);

        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 1;
        exercise.buildConfig = undefined;

        const dto = toUpdateProgrammingExerciseDTO(exercise);

        expect(dto.id).toBe(1);
        expect(dto.buildConfig).toBeUndefined();
        expect(dto.gradingCriteria).toBeUndefined();
        expect(dto.auxiliaryRepositories).toBeUndefined();
        expect(dto.competencyLinks).toBeUndefined();
        expect(dto.showTestNamesToStudents).toBeFalse();
        expect(dto.allowOnlineIde).toBeFalse();
        expect(dto.releaseTestsWithExampleSolution).toBeFalse();
    });
});
