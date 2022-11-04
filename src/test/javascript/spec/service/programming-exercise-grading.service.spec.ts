import { TestBed } from '@angular/core/testing';
import { of, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ProgrammingExerciseGradingService } from 'app/exercises/programming/manage/services/programming-exercise-grading.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { Result } from 'app/entities/result.model';
import { HttpClient } from '@angular/common/http';

describe('ProgrammingExerciseGradingService', () => {
    let websocketService: JhiWebsocketService;
    let httpService: HttpClient;
    let exercise1TestCaseSubject: Subject<Result>;
    let exercise2TestCaseSubject: Subject<Result>;
    let receiveStub: jest.SpyInstance;
    let getStub: jest.SpyInstance;

    let gradingService: ProgrammingExerciseGradingService;

    const exercise1 = { id: 1 };
    const exercise2 = { id: 2 };

    const testCases1 = [
        { testName: 'testBubbleSort', active: true },
        { testName: 'testMergeSort', active: true },
        { testName: 'otherTest', active: false },
    ] as ProgrammingExerciseTestCase[];
    const testCases2 = [
        { testName: 'testBubbleSort', active: false },
        { testName: 'testMergeSort', active: true },
        { testName: 'otherTest', active: true },
    ] as ProgrammingExerciseTestCase[];

    const exercise1Topic = `/topic/programming-exercises/${exercise1.id}/test-cases`;
    const exercise2Topic = `/topic/programming-exercises/${exercise2.id}/test-cases`;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: HttpClient, useClass: MockHttpService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
            ],
        })
            .compileComponents()
            .then(() => {
                websocketService = TestBed.inject(JhiWebsocketService);
                httpService = TestBed.inject(HttpClient);
                gradingService = TestBed.inject(ProgrammingExerciseGradingService);

                receiveStub = jest.spyOn(websocketService, 'receive');
                getStub = jest.spyOn(httpService, 'get');

                exercise1TestCaseSubject = new Subject();
                exercise2TestCaseSubject = new Subject();
                receiveStub.mockImplementation((arg1) => {
                    switch (arg1) {
                        case exercise1Topic:
                            return exercise1TestCaseSubject;
                        case exercise2Topic:
                            return exercise2TestCaseSubject;
                    }
                });
                getStub.mockImplementation((arg1) => {
                    switch (arg1) {
                        case `${gradingService.resourceUrl}/${exercise1.id}/test-cases`:
                            return of(testCases1);
                        case `${gradingService.resourceUrl}/${exercise2.id}/test-cases`:
                            return of(testCases2);
                    }
                });
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch the test cases via REST call if there is nothing stored yet for a given exercise', () => {
        let testCasesExercise1;
        let testCasesExercise2;

        gradingService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap((newTestCases) => (testCasesExercise1 = newTestCases)))
            .subscribe();

        expect(getStub).toHaveBeenCalledOnce();
        expect(testCasesExercise1).toEqual(testCases1);
        expect(testCasesExercise2).toBeUndefined();

        gradingService
            .subscribeForTestCases(exercise2.id)
            .pipe(tap((newTestCases) => (testCasesExercise2 = newTestCases)))
            .subscribe();

        expect(getStub).toHaveBeenCalledTimes(2);
        expect(testCasesExercise1).toEqual(testCases1);
        expect(testCasesExercise2).toEqual(testCases2);
    });

    it('should reuse the same subject when there already is a connection established and not call the REST endpoint', () => {
        let testCasesExercise1;
        // Subscriber 1.
        gradingService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap((newTestCases) => (testCasesExercise1 = newTestCases)))
            .subscribe();
        // Subscriber 2.
        gradingService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap((newTestCases) => (testCasesExercise1 = newTestCases)))
            .subscribe();

        expect(getStub).toHaveBeenCalledOnce();
        expect(testCasesExercise1).toEqual(testCases1);
    });

    it('should notify subscribers on new test case value', () => {
        const newTestCasesOracle = testCases1.map((testCase) => ({ ...testCase, weight: 30 }));
        let testCasesExercise1Subscriber1;
        let testCasesExercise1Subscriber2;
        // Subscriber 1.
        gradingService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap((newTestCases) => (testCasesExercise1Subscriber1 = newTestCases)))
            .subscribe();
        // Subscriber 2.
        gradingService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap((newTestCases) => (testCasesExercise1Subscriber2 = newTestCases)))
            .subscribe();

        expect(testCasesExercise1Subscriber1).toEqual(testCases1);
        expect(testCasesExercise1Subscriber2).toEqual(testCases1);

        gradingService.notifyTestCases(exercise1.id, newTestCasesOracle);

        expect(testCasesExercise1Subscriber1).toEqual(newTestCasesOracle);
        expect(testCasesExercise1Subscriber2).toEqual(newTestCasesOracle);
    });
});
