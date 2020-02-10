import { async } from '@angular/core/testing';
import * as chai from 'chai';
import { SinonSpy, SinonStub, spy, stub } from 'sinon';
import { of, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import * as sinonChai from 'sinon-chai';
import { Participation } from '../../../../main/webapp/app/entities/participation';
import { MockWebsocketService } from '../mocks/mock-websocket.service';
import { IWebsocketService } from 'app/core/websocket/websocket.service.ts';
import { Result } from '../../../../main/webapp/app/entities/result';
import { ProgrammingExerciseTestCaseService } from 'app/entities/programming-exercise/services/programming-exercise-test-case.service';
import { MockHttpService } from '../mocks/mock-http.service';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise/programming-exercise-test-case.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('ProgrammingExerciseTestCaseService', () => {
    let websocketService: IWebsocketService;
    let httpService: MockHttpService;
    let exercise1TestCaseSubject: Subject<Result>;
    let receiveParticipationSubject: Subject<Participation>;
    let exercise2TestCaseSubject: Subject<Result>;
    let receiveParticipation2Subject: Subject<Participation>;
    let subscribeSpy: SinonSpy;
    let receiveStub: SinonStub;
    let unsubscribeSpy: SinonSpy;
    let getStub: SinonStub;

    let testCaseService: ProgrammingExerciseTestCaseService;

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

    const exercise1Topic = `/topic/programming-exercise/${exercise1.id}/test-cases`;
    const exercise2Topic = `/topic/programming-exercise/${exercise2.id}/test-cases`;

    beforeEach(async(() => {
        websocketService = new MockWebsocketService();
        httpService = new MockHttpService();
        testCaseService = new ProgrammingExerciseTestCaseService(websocketService as any, httpService as any);

        subscribeSpy = spy(websocketService, 'subscribe');
        unsubscribeSpy = spy(websocketService, 'unsubscribe');
        receiveStub = stub(websocketService, 'receive');
        getStub = stub(httpService, 'get');

        exercise1TestCaseSubject = new Subject();
        exercise2TestCaseSubject = new Subject();
        receiveParticipationSubject = new Subject();
        receiveParticipation2Subject = new Subject();
        receiveStub.withArgs(exercise1Topic).returns(exercise1TestCaseSubject);
        receiveStub.withArgs(exercise2Topic).returns(exercise2TestCaseSubject);
        getStub.withArgs(`${testCaseService.testCaseUrl}/${exercise1.id}/test-cases`).returns(of(testCases1));
        getStub.withArgs(`${testCaseService.testCaseUrl}/${exercise2.id}/test-cases`).returns(of(testCases2));
    }));

    afterEach(() => {
        subscribeSpy.restore();
        unsubscribeSpy.restore();
        receiveStub.restore();
        getStub.restore();
    });

    it('should fetch the test cases via REST call if there is nothing stored yet for a given exercise', () => {
        let testCasesExercise1;
        let testCasesExercise2;

        testCaseService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap(newTestCases => (testCasesExercise1 = newTestCases)))
            .subscribe();

        expect(getStub).to.have.been.calledOnce;
        expect(testCasesExercise1).to.deep.equal(testCases1);
        expect(testCasesExercise2).to.be.undefined;

        testCaseService
            .subscribeForTestCases(exercise2.id)
            .pipe(tap(newTestCases => (testCasesExercise2 = newTestCases)))
            .subscribe();

        expect(getStub).to.have.been.calledTwice;
        expect(testCasesExercise1).to.deep.equal(testCases1);
        expect(testCasesExercise2).to.deep.equal(testCases2);
    });

    it('should reuse the same subject when there already is a connection established and not call the REST endpoint', () => {
        let testCasesExercise1;
        // Subscriber 1.
        testCaseService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap(newTestCases => (testCasesExercise1 = newTestCases)))
            .subscribe();
        // Subscriber 2.
        testCaseService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap(newTestCases => (testCasesExercise1 = newTestCases)))
            .subscribe();

        expect(getStub).to.have.been.calledOnce;
        expect(testCasesExercise1).to.equal(testCases1);
    });

    it('should notify subscribers on new test case value', () => {
        const newTestCases = testCases1.map(testCase => ({ ...testCase, weight: 30 }));
        let testCasesExercise1Subscriber1;
        let testCasesExercise1Subscriber2;
        // Subscriber 1.
        testCaseService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap(newTestCases => (testCasesExercise1Subscriber1 = newTestCases)))
            .subscribe();
        // Subscriber 2.
        testCaseService
            .subscribeForTestCases(exercise1.id)
            .pipe(tap(newTestCases => (testCasesExercise1Subscriber2 = newTestCases)))
            .subscribe();

        expect(testCasesExercise1Subscriber1).to.equal(testCases1);
        expect(testCasesExercise1Subscriber2).to.equal(testCases1);

        testCaseService.notifyTestCases(exercise1.id, newTestCases);

        expect(testCasesExercise1Subscriber1).to.equal(newTestCases);
        expect(testCasesExercise1Subscriber2).to.equal(newTestCases);
    });
});
