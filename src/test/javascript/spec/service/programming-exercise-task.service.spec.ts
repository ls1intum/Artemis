import { ProgrammingExerciseTaskService } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task.service';
import { TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../test.module';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AlertService } from 'app/core/util/alert.service';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ProgrammingExerciseGradingStatistics, TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';
import { MockProvider } from 'ng-mocks';
import { MockProgrammingExerciseGradingService } from '../helpers/mocks/service/mock-programming-exercise-grading.service';
import { ProgrammingExerciseTask } from 'app/exercises/programming/manage/grading/tasks/programming-exercise-task';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';
import { ProgrammingExerciseTestCase, ProgrammingExerciseTestCaseType, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { firstValueFrom, of } from 'rxjs';

describe('ProgrammingExerciseTask Service', () => {
    let service: ProgrammingExerciseTaskService;

    let alertService;
    let gradingService;
    let alertErrorStub: jest.SpyInstance;
    let alertSuccessStub: jest.SpyInstance;
    let updateTestCasesStub: jest.SpyInstance;
    let resetTestCasesStub: jest.SpyInstance;

    const resourceUrl = 'api/programming-exercises';
    let httpMock: HttpTestingController;
    const serverSideTasks: ProgrammingExerciseServerSideTask[] = [
        {
            id: 1,
            taskName: 'weightTask',
            testCases: [
                {
                    id: 1,
                    testName: 'test1',
                    weight: 1,
                    active: true,
                },
                {
                    id: 2,
                    testName: 'test2',
                    weight: 1,
                    active: true,
                },
            ],
        },
        {
            id: 2,
            taskName: 'bonusTask',
            testCases: [
                {
                    id: 3,
                    testName: 'test3',
                    bonusPoints: 2,
                    active: true,
                },
                {
                    id: 4,
                    testName: 'test4',
                    bonusPoints: 2,
                    active: true,
                },
            ],
        },
        {
            id: 3,
            taskName: 'sameOptionsTask',
            testCases: [
                {
                    id: 5,
                    testName: 'test5',
                    visibility: Visibility.Always,
                    bonusMultiplier: 2,
                    active: true,
                    type: ProgrammingExerciseTestCaseType.BEHAVIORAL,
                },
                {
                    id: 6,
                    testName: 'test6',
                    visibility: Visibility.Always,
                    bonusMultiplier: 2,
                    active: true,
                    type: ProgrammingExerciseTestCaseType.BEHAVIORAL,
                },
            ],
        },
        {
            id: 4,
            taskName: 'inactiveTask',
            testCases: [
                {
                    id: 7,
                    testName: 'test7',
                    active: false,
                },
                {
                    id: 8,
                    testName: 'test8',
                    active: false,
                },
            ],
        },
    ];

    const exercise: Partial<ProgrammingExercise> = { id: 1, maxPoints: 42 };
    const course: Partial<Course> = { id: 2, title: 'test' };
    const gradingStatistics: ProgrammingExerciseGradingStatistics = {
        testCaseStatsMap: {
            test1: {
                numPassed: 1,
                numFailed: 2,
            },
            test2: {
                numPassed: 3,
                numFailed: 5,
            },
            test3: {
                numPassed: 7,
                numFailed: 11,
            },
        },
    };

    let tasks: ProgrammingExerciseTask[];
    let testCases: ProgrammingExerciseTestCase[];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [
                ProgrammingExerciseTaskService,
                { provide: ProgrammingExerciseGradingService, useClass: MockProgrammingExerciseGradingService },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseTaskService);
                httpMock = TestBed.inject(HttpTestingController);
                alertService = TestBed.inject(AlertService);
                alertSuccessStub = jest.spyOn(alertService, 'success');
                alertErrorStub = jest.spyOn(alertService, 'error');
                gradingService = TestBed.inject(ProgrammingExerciseGradingService);
                updateTestCasesStub = jest.spyOn(gradingService, 'updateTestCase');
                resetTestCasesStub = jest.spyOn(gradingService, 'resetTestCases');

                firstValueFrom(service.configure(exercise as ProgrammingExercise, course, gradingStatistics)).then(() => {
                    tasks = service.updateTasks();
                    testCases = tasks.flatMap(({ testCases }) => testCases);
                });

                httpMock.expectOne(`${resourceUrl}/${exercise.id}/tasks-with-unassigned-test-cases`).flush(serverSideTasks);
            });
    });

    it('should get tasks from server', () => {
        expect(service.currentTasks).not.toBeEmpty();
        expect(service.tasks).not.toBeEmpty();
        expect(tasks).not.toBeEmpty();
        expect(testCases).not.toBeEmpty();
    });

    it('should create correct task options from test cases', () => {
        const expected = [
            {
                bonusPoints: 0,
                taskName: 'weightTask',
                weight: 2,
            },
            {
                bonusPoints: 4,
                taskName: 'bonusTask',
                weight: 0,
            },
            {
                bonusMultiplier: 2,
                bonusPoints: 0,
                taskName: 'sameOptionsTask',
                type: 'BEHAVIORAL',
                visibility: 'ALWAYS',
                weight: 0,
            },
        ];
        expect(
            tasks.map((task) => ({
                taskName: task.taskName,
                weight: task.weight,
                bonusMultiplier: task.bonusMultiplier,
                bonusPoints: task.bonusPoints,
                visibility: task.visibility,
                type: task.type,
            })),
        ).toEqual(expected);
    });

    it('should assign tests correct grading statistics', () => {
        const stats = testCases.map(({ testCaseStats }) => testCaseStats).filter((stats) => stats);
        const expected: TestCaseStats[] = [
            {
                numFailed: 2,
                numPassed: 1,
            },
            {
                numFailed: 5,
                numPassed: 3,
            },
            {
                numFailed: 11,
                numPassed: 7,
            },
        ];
        expect(stats).toEqual(expected);
    });

    it('should handle inactive test cases correctly if they are ignored', () => {
        // By default ignores inactive
        tasks = service.updateTasks();
        const testCases = tasks.flatMap(({ testCases }) => testCases);

        expect(testCases).toSatisfyAll((test) => test.active);
    });

    it('should handle inactive test cases correctly if they are not ignored', () => {
        service.toggleIgnoreInactive();
        tasks = service.updateTasks();
        const testCases = tasks.flatMap(({ testCases }) => testCases);

        expect(testCases.filter((testCase) => testCase.active)).not.toBeEmpty();
    });

    it('should update test resulting points correctly', () => {
        const testCase = testCases.find(({ id }) => id === 1)!;

        expect([testCase.resultingPoints, testCase.resultingPointsPercent]).toStrictEqual([21, 50]);

        // update grading settings
        testCase.weight = 2;
        testCase.bonusMultiplier = 2;
        testCase.bonusPoints = 5;

        service.updateTasks();

        expect([testCase.resultingPoints, testCase.resultingPointsPercent]).toStrictEqual([61, 145.2]);
    });

    it('should save test case configuration', () => {
        testCases.forEach((testCase) => {
            testCase.changed = true;
        });

        service.saveTestCases();

        expect(updateTestCasesStub).toHaveBeenCalled();
    });

    it('should show an error alert when test case weights below zero when saving', () => {
        testCases.forEach((testCase) => {
            testCase.weight = -1;
            testCase.changed = true;
        });

        // Initialize spy for error alert
        updateTestCasesStub.mockReturnValue(of(testCases.map((test) => ({ ...test, weight: 0 }))));

        return firstValueFrom(service.saveTestCases()).then(() => {
            expect(alertErrorStub).toHaveBeenCalled();
        });
    });

    it('should NOT show an error alert when test case weights zero when saving', () => {
        testCases.forEach((testCase) => {
            testCase.weight = 0;
            testCase.changed = true;
        });

        updateTestCasesStub.mockReturnValue(of(testCases.map((test) => ({ ...test, weight: 0 }))));

        return firstValueFrom(service.saveTestCases()).then(() => {
            expect(alertErrorStub).not.toHaveBeenCalled();
        });
    });

    it('should reset test case configuration', () => {
        resetTestCasesStub.mockReturnValue(of(tasks.flatMap(({ testCases }) => testCases)));

        const done = firstValueFrom(service.resetTestCases()).then(() => {
            expect(resetTestCasesStub).toHaveBeenCalled();
            expect(alertSuccessStub).toHaveBeenCalled();
        });

        httpMock.expectOne(`${resourceUrl}/${exercise.id}/tasks-with-unassigned-test-cases`).flush([]);

        return done;
    });

    it('should correctly detect unsaved changes', () => {
        expect(service.hasUnsavedChanges()).toBeFalse();

        const testCase = testCases.find(({ id }) => id === 1)!;
        testCase.weight = 10;
        testCase.changed = true;

        expect(service.hasUnsavedChanges()).toBeTrue();
    });
});
