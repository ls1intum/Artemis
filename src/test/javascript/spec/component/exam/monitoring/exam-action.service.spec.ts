import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { createActions } from './exam-monitoring-helper';
import { ExamActionService } from 'app/exam/monitoring/exam-action.service';
import dayjs from 'dayjs/esm';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';

describe('ExamActionService', () => {
    let examActionService: ExamActionService;
    let httpClient: HttpClient;
    let exam: Exam;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                ArtemisServerDateService,
                ExamActionService,
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: HttpClient, useClass: MockHttpService },
            ],
        })
            .compileComponents()
            .then(() => {
                examActionService = TestBed.inject(ExamActionService);
                httpClient = TestBed.inject(HttpClient);
            });
    });

    beforeEach(() => {
        // reset exam
        exam = new Exam();
        exam.id = 1;
        exam.monitoring = true;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    // Prepare actions
    it.each(createActions())('should prepare action', (examAction: ExamAction) => {
        const now = dayjs().set('seconds', 8).set('ms', 0);
        examAction.timestamp = now;

        examActionService.prepareAction(examAction);

        expect(examAction.timestamp).toEqual(now);
        examAction.ceiledTimestamp!.set('ms', 0);
        expect(examAction.ceiledTimestamp).toEqual(now.set('seconds', 15));
    });

    // Load initial actions
    it('should load initial actions', () => {
        const spy = jest.spyOn(httpClient, 'get');
        const initialActionsLoaded = new Map<number, boolean>();

        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);

        examActionService.loadInitialActions(exam);

        initialActionsLoaded.set(exam.id!, true);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(`api/exam-monitoring/${exam.id}/load-actions`);
        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);
    });

    // increase action by timestamp
    it('should increase action by timestamp', () => {
        const actionsPerTimestamp = new Map();
        const timestamp = dayjs().toString();

        examActionService.increaseActionByTimestamp(timestamp, actionsPerTimestamp);

        const expected = new Map();
        expected.set(timestamp, 1);
        expect(actionsPerTimestamp).toEqual(expected);

        examActionService.increaseActionByTimestamp(timestamp, actionsPerTimestamp);

        expected.set(timestamp, 2);
        expect(actionsPerTimestamp).toEqual(expected);
    });

    // increase action by timestamp and category
    it.each(createActions())('should increase action by timestamp and category', (action: ExamAction) => {
        const actionsPerTimestampAndCategory = new Map();
        const timestamp = dayjs().toString();

        examActionService.increaseActionByTimestampAndCategory(timestamp, action, actionsPerTimestampAndCategory);

        const expected = new Map();
        const categories = new Map();
        Object.keys(ExamActionType).forEach((type) => {
            categories!.set(type, 0);
        });

        categories.set(action.type, 1);
        expected.set(timestamp, categories);
        expect(actionsPerTimestampAndCategory).toEqual(expected);

        examActionService.increaseActionByTimestampAndCategory(timestamp, action, actionsPerTimestampAndCategory);

        categories.set(action.type, 2);
        expected.set(timestamp, categories);
        expect(actionsPerTimestampAndCategory).toEqual(expected);
    });

    // update last action by student
    it.each(createActions())('should update last action by student', (action: ExamAction) => {
        const lastActionPerStudent = new Map();
        action.examActivityId = 1;

        examActionService.updateLastActionPerStudent(action, lastActionPerStudent);

        const expected = new Map();
        expected.set(action.examActivityId, action);
        expect(lastActionPerStudent).toEqual(expected);

        action.examActivityId = 2;
        examActionService.updateLastActionPerStudent(action, lastActionPerStudent);

        expected.set(action.examActivityId, action);
        expect(lastActionPerStudent).toEqual(expected);
    });

    // update navigations per student
    it.each(createActions())('should update navigations per student', (action: ExamAction) => {
        const navigatedToPerStudent = new Map();
        action.examActivityId = 1;

        examActionService.updateNavigationsPerStudent(action, navigatedToPerStudent);

        const expected = new Map();
        if (action.type === ExamActionType.SWITCHED_EXERCISE) {
            expected.set(action.examActivityId, new Set([(action as SwitchedExerciseAction).exerciseId]));
        }
        expect(navigatedToPerStudent).toEqual(expected);

        action.examActivityId = 2;
        examActionService.updateNavigationsPerStudent(action, navigatedToPerStudent);

        if (action.type === ExamActionType.SWITCHED_EXERCISE) {
            expected.set(action.examActivityId, new Set([(action as SwitchedExerciseAction).exerciseId]));
        }
        expect(navigatedToPerStudent).toEqual(expected);
    });

    // update submissions per student
    it.each(createActions())('should update submissions per student', (action: ExamAction) => {
        const submittedPerStudent = new Map();
        action.examActivityId = 1;

        examActionService.updateSubmissionsPerStudent(action, submittedPerStudent);

        const expected = new Map();
        if (action.type === ExamActionType.SAVED_EXERCISE) {
            expected.set(action.examActivityId, new Set([(action as SavedExerciseAction).exerciseId]));
        }
        expect(submittedPerStudent).toEqual(expected);

        action.examActivityId = 2;
        examActionService.updateSubmissionsPerStudent(action, submittedPerStudent);

        if (action.type === ExamActionType.SAVED_EXERCISE) {
            expected.set(action.examActivityId, new Set([(action as SavedExerciseAction).exerciseId]));
        }
        expect(submittedPerStudent).toEqual(expected);
    });
});
