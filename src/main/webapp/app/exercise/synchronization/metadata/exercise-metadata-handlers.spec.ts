import { describe, expect, it } from 'vitest';
import dayjs from 'dayjs/esm';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { createBaseHandlers, createExerciseMetadataHandlers } from 'app/exercise/synchronization/metadata/exercise-metadata-handlers';
import { ExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';

const extractKeys = (handlers: { key: string }[]) => handlers.map((handler) => handler.key);

describe('ExerciseMetadataHandlers', () => {
    it('includes base fields for programming exercises', () => {
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const keys = extractKeys(handlers);

        expect(keys).toContain('title');
        expect(keys).toContain('shortName');
        expect(keys).toContain('competencyLinks');
        expect(keys).toContain('channelName');
        expect(keys).toContain('maxPoints');
        // Note: problemStatement is NOT included - it's handled by live Yjs synchronization
        expect(keys).not.toContain('problemStatement');
    });

    it('includes programming-specific fields', () => {
        const programmingHandlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const keys = extractKeys(programmingHandlers);

        expect(keys).toContain('programmingData.showTestNamesToStudents');
        expect(keys).toContain('programmingData.allowOnlineEditor');
        expect(keys).toContain('programmingData.buildConfig');
        expect(keys).toContain('programmingData.auxiliaryRepositories');
    });

    it('includes only base handlers for non-programming exercise types', () => {
        const textHandlers = createExerciseMetadataHandlers(ExerciseType.TEXT);
        const modelingHandlers = createExerciseMetadataHandlers(ExerciseType.MODELING);
        const quizHandlers = createExerciseMetadataHandlers(ExerciseType.QUIZ);

        // All types get base handlers (title, shortName, etc.)
        expect(textHandlers.length).toBeGreaterThan(0);
        expect(modelingHandlers.length).toBeGreaterThan(0);
        expect(quizHandlers.length).toBeGreaterThan(0);

        // But non-programming types should not have type-specific handlers
        expect(extractKeys(textHandlers)).not.toContain('textData.exampleSolution');
        expect(extractKeys(modelingHandlers)).not.toContain('modelingData.diagramType');
        expect(extractKeys(quizHandlers)).not.toContain('quizData.quizQuestions');
    });

    it('maps incoming values and applies all handlers for programming exercises', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.title = 'local title';
        exercise.shortName = 'local-short';
        exercise.maxPoints = 1;
        exercise.programmingLanguage = ProgrammingLanguage.JAVA;
        exercise.buildConfig!.sequentialTestRuns = false;

        const competency = new Competency();
        competency.id = 99;
        competency.title = 'Comp 99';
        exercise.course = { competencies: [competency], prerequisites: [] } as any;

        const snapshot: ExerciseSnapshotDTO = {
            id: 1,
            title: 'incoming title',
            shortName: 'incoming-short',
            channelName: 'incoming-channel',
            maxPoints: 10,
            bonusPoints: 2,
            releaseDate: '2026-01-01T00:00:00.000Z',
            startDate: '2026-01-02T00:00:00.000Z',
            dueDate: '2026-01-03T00:00:00.000Z',
            assessmentDueDate: '2026-01-04T00:00:00.000Z',
            exampleSolutionPublicationDate: '2026-01-05T00:00:00.000Z',
            gradingInstructions: 'incoming instructions',
            categories: ['alpha', 'beta'],
            teamAssignmentConfig: { id: 7, minTeamSize: 2, maxTeamSize: 4 },
            secondCorrectionEnabled: true,
            feedbackSuggestionModule: 'module',
            competencyLinks: [{ competencyId: { competencyId: 99 }, weight: 0.5 }],
            programmingData: {
                auxiliaryRepositories: [
                    { id: 11, name: 'aux-1', checkoutDirectory: 'aux-1', description: 'first auxiliary repository' },
                    { id: 12, name: 'aux-2', checkoutDirectory: 'aux-2', description: 'second auxiliary repository' },
                ],
                allowOnlineEditor: true,
                allowOfflineIde: false,
                allowOnlineIde: true,
                maxStaticCodeAnalysisPenalty: 25,
                showTestNamesToStudents: true,
                buildAndTestStudentSubmissionsAfterDueDate: '2026-02-01T00:00:00.000Z',
                releaseTestsWithExampleSolution: true,
                buildConfig: {
                    sequentialTestRuns: true,
                    buildPlanConfiguration: 'Maven',
                    buildScript: 'gradle test',
                    checkoutSolutionRepository: true,
                    timeoutSeconds: 45,
                    allowBranching: true,
                    branchRegex: 'feature/.*',
                },
            },
        };

        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING, () => exercise);
        const handlerMap = new Map(handlers.map((handler) => [handler.key, handler]));

        for (const handler of handlers) {
            const incomingValue = handler.getIncomingValue(snapshot);
            handler.getCurrentValue(exercise);
            handler.getBaselineValue(exercise);
            handler.applyValue(exercise, incomingValue as any);
        }

        expect(exercise.title).toBe('incoming title');
        expect(exercise.shortName).toBe('incoming-short');
        expect(exercise.channelName).toBe('incoming-channel');
        expect(exercise.maxPoints).toBe(10);
        expect(exercise.bonusPoints).toBe(2);
        expect(dayjs.isDayjs(exercise.releaseDate)).toBe(true);
        expect(dayjs.isDayjs(exercise.startDate)).toBe(true);
        expect(dayjs.isDayjs(exercise.dueDate)).toBe(true);
        expect(dayjs.isDayjs(exercise.assessmentDueDate)).toBe(true);
        expect(dayjs.isDayjs(exercise.exampleSolutionPublicationDate)).toBe(true);
        expect(exercise.categories?.map((category) => category.category)).toEqual(['alpha', 'beta']);
        expect(exercise.teamAssignmentConfig?.id).toBe(7);
        expect(exercise.secondCorrectionEnabled).toBe(true);
        expect(exercise.feedbackSuggestionModule).toBe('module');
        expect(exercise.allowOnlineEditor).toBe(true);
        expect(exercise.allowOfflineIde).toBe(false);
        expect(exercise.allowOnlineIde).toBe(true);
        expect(exercise.maxStaticCodeAnalysisPenalty).toBe(25);
        expect(exercise.showTestNamesToStudents).toBe(true);
        expect(dayjs.isDayjs(exercise.buildAndTestStudentSubmissionsAfterDueDate)).toBe(true);
        expect(exercise.releaseTestsWithExampleSolution).toBe(true);
        expect(exercise.auxiliaryRepositories).toHaveLength(2);
        expect(exercise.auxiliaryRepositories?.[0].name).toBe('aux-1');
        expect(exercise.auxiliaryRepositories?.[0].checkoutDirectory).toBe('aux-1');
        expect(exercise.auxiliaryRepositories?.[0].description).toBe('first auxiliary repository');
        expect(exercise.competencyLinks).toHaveLength(1);
        expect(exercise.competencyLinks?.[0].competency?.id).toBe(99);
        expect(exercise.competencyLinks?.[0].weight).toBe(0.5);
        expect(exercise.buildConfig?.sequentialTestRuns).toBe(true);
        expect(exercise.buildConfig?.buildPlanConfiguration).toBe('Maven');

        const buildConfigHandler = handlerMap.get('programmingData.buildConfig')!;
        buildConfigHandler.applyValue(exercise, undefined as any);
        expect(exercise.buildConfig).toBeUndefined();
    });

    it('normalizes date getCurrentValue and getBaselineValue through convertDateFromServer', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.releaseDate = dayjs('2026-03-15T12:00:00.000Z');
        exercise.buildAndTestStudentSubmissionsAfterDueDate = dayjs('2026-04-01T08:00:00.000Z');

        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const releaseDateHandler = handlers.find((handler) => handler.key === 'releaseDate')!;
        const buildTestDateHandler = handlers.find((handler) => handler.key === 'programmingData.buildAndTestStudentSubmissionsAfterDueDate')!;

        const currentRelease = releaseDateHandler.getCurrentValue(exercise);
        const baselineRelease = releaseDateHandler.getBaselineValue(exercise);
        const currentBuildTest = buildTestDateHandler.getCurrentValue(exercise);
        const baselineBuildTest = buildTestDateHandler.getBaselineValue(exercise);

        expect(dayjs.isDayjs(currentRelease)).toBe(true);
        expect(dayjs.isDayjs(baselineRelease)).toBe(true);
        expect(dayjs.isDayjs(currentBuildTest)).toBe(true);
        expect(dayjs.isDayjs(baselineBuildTest)).toBe(true);
        expect((currentRelease as dayjs.Dayjs).toISOString()).toBe('2026-03-15T12:00:00.000Z');
        expect((currentBuildTest as dayjs.Dayjs).toISOString()).toBe('2026-04-01T08:00:00.000Z');
    });

    it('returns undefined for absent date fields via getCurrentValue', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const releaseDateHandler = handlers.find((handler) => handler.key === 'releaseDate')!;

        expect(releaseDateHandler.getCurrentValue(exercise)).toBeUndefined();
        expect(releaseDateHandler.getBaselineValue(exercise)).toBeUndefined();
    });

    it('uses raw snapshot competency links when no resolver is provided', () => {
        const baseHandlers = createBaseHandlers();
        const competencyHandler = baseHandlers.find((handler) => handler.key === 'competencyLinks')!;
        const snapshot: ExerciseSnapshotDTO = {
            id: 1,
            competencyLinks: [{ competencyId: { competencyId: 7 }, weight: 1 }],
        };

        const incoming = competencyHandler.getIncomingValue(snapshot) as any[];

        expect(incoming).toHaveLength(1);
        expect(incoming[0].competencyId.competencyId).toBe(7);
        expect(incoming[0].weight).toBe(1);
    });

    it('applies secondCorrectionEnabled fallback to false for undefined values', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.secondCorrectionEnabled = true;
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const secondCorrectionHandler = handlers.find((handler) => handler.key === 'secondCorrectionEnabled')!;

        secondCorrectionHandler.applyValue(exercise, undefined as any);

        expect(exercise.secondCorrectionEnabled).toBe(false);
    });

    it('updates categories in place to keep existing references synchronized', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.categories = [new ExerciseCategory('local', '#111111')];
        const categoriesReference = exercise.categories;
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const categoriesHandler = handlers.find((handler) => handler.key === 'categories')!;

        categoriesHandler.applyValue(exercise, ['alpha', 'beta'] as any);

        expect(exercise.categories).toBe(categoriesReference);
        expect(exercise.categories?.map((category) => category.category)).toEqual(['alpha', 'beta']);

        categoriesHandler.applyValue(exercise, [] as any);

        expect(exercise.categories).toBe(categoriesReference);
        expect(exercise.categories).toEqual([]);
    });

    it('normalizes snapshot category strings into ExerciseCategory objects in getIncomingValue', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.categories = [new ExerciseCategory('alpha', '#123456'), new ExerciseCategory('beta', '#abcdef')];
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const categoriesHandler = handlers.find((handler) => handler.key === 'categories')!;

        const snapshot: ExerciseSnapshotDTO = {
            id: 1,
            categories: ['{"category":"alpha","color":"#123456"}', '{"category":"beta","color":"#abcdef"}'],
        };

        const incoming = categoriesHandler.getIncomingValue(snapshot) as ExerciseCategory[];
        const current = categoriesHandler.getCurrentValue(exercise) as ExerciseCategory[];

        expect(incoming).toHaveLength(2);
        expect(incoming[0]).toBeInstanceOf(ExerciseCategory);
        expect(incoming[1]).toBeInstanceOf(ExerciseCategory);
        expect(incoming[0].category).toBe('alpha');
        expect(incoming[0].color).toBe('#123456');
        expect(incoming[1].category).toBe('beta');
        expect(incoming[1].color).toBe('#abcdef');

        // Verify structural equality so metadataValuesEqual (which uses isEqual) won't produce false conflicts
        expect(incoming).toEqual(current);
    });

    it('parses JSON-encoded category strings when applying category updates', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.categories = [new ExerciseCategory('local', '#111111')];
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const categoriesHandler = handlers.find((handler) => handler.key === 'categories')!;

        categoriesHandler.applyValue(exercise, ['{"category":"alpha","color":"#123456"}', '{"category":"beta","color":"#abcdef"}'] as any);

        expect(exercise.categories?.map((category) => category.category)).toEqual(['alpha', 'beta']);
        expect(exercise.categories?.map((category) => category.color)).toEqual(['#123456', '#abcdef']);
    });

    it('updates auxiliary repositories by assigning a fresh array', () => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.auxiliaryRepositories = [
            {
                id: 1,
                name: 'local',
                checkoutDirectory: 'local',
                description: 'local description',
                repositoryUri: 'git@server:local.git',
            } as any,
        ];
        const auxiliaryRepositoriesReference = exercise.auxiliaryRepositories;
        const handlers = createExerciseMetadataHandlers(ExerciseType.PROGRAMMING);
        const auxiliaryRepositoriesHandler = handlers.find((handler) => handler.key === 'programmingData.auxiliaryRepositories')!;

        auxiliaryRepositoriesHandler.applyValue(exercise, [
            {
                id: 10,
                name: 'aux-a',
                checkoutDirectory: 'dir-a',
                description: 'desc-a',
                repositoryUri: 'git@server:aux-a.git',
            },
        ] as any);

        expect(exercise.auxiliaryRepositories).not.toBe(auxiliaryRepositoriesReference);
        expect(exercise.auxiliaryRepositories).toHaveLength(1);
        expect(exercise.auxiliaryRepositories?.[0].name).toBe('aux-a');
        expect(exercise.auxiliaryRepositories?.[0].checkoutDirectory).toBe('dir-a');
        expect(exercise.auxiliaryRepositories?.[0].description).toBe('desc-a');

        auxiliaryRepositoriesHandler.applyValue(exercise, undefined as any);

        expect(exercise.auxiliaryRepositories).not.toBe(auxiliaryRepositoriesReference);
        expect(exercise.auxiliaryRepositories).toEqual([]);
    });
});
