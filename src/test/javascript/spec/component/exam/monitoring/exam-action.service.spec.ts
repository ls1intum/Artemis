import { TestBed } from '@angular/core/testing';
import { Exam } from 'app/entities/exam.model';
import { ArtemisTestModule } from '../../../test.module';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MockWebsocketService } from '../../../helpers/mocks/service/mock-websocket.service';
import { createActions } from './exam-monitoring-helper';
import { BehaviorSubject, of } from 'rxjs';
import { EXAM_MONITORING_ACTION_TOPIC, EXAM_MONITORING_ACTIONS_TOPIC, EXAM_MONITORING_STATUS_TOPIC, ExamActionService } from 'app/exam/monitoring/exam-action.service';
import dayjs from 'dayjs/esm';
import { MockHttpService } from '../../../helpers/mocks/service/mock-http.service';
import { HttpClient } from '@angular/common/http';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

describe('ExamActionService', () => {
    let examActionService: ExamActionService;
    let httpClient: HttpClient;
    let websocketService: JhiWebsocketService;
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
                websocketService = TestBed.inject(JhiWebsocketService);
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

    // Notify exam monitoring update subscribers
    it.each([true, false])('should notify exam monitoring update subscribers', (status: boolean) => {
        const examMonitoringStatusObservables = new Map<number, BehaviorSubject<boolean>>();
        expect(examActionService.examMonitoringStatusObservables).toEqual(examMonitoringStatusObservables);

        examActionService.notifyExamMonitoringUpdateSubscribers(exam, status);

        examMonitoringStatusObservables.set(exam.id!, new BehaviorSubject(status));

        expect(examActionService.examMonitoringStatusObservables).toEqual(examMonitoringStatusObservables);

        examActionService.notifyExamMonitoringUpdateSubscribers(exam, !status);

        examMonitoringStatusObservables.get(exam.id!)!.next(!status);

        expect(examActionService.examMonitoringStatusObservables).toEqual(examMonitoringStatusObservables);
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
        expect(spy).toHaveBeenCalledWith(`api/exams/${exam.id}/load-actions`);
        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);
    });

    it('should not load initial actions', () => {
        const spy = jest.spyOn(httpClient, 'get');
        const initialActionsLoaded = new Map<number, boolean>();
        initialActionsLoaded.set(exam.id!, true);
        examActionService.initialActionsLoaded.set(exam.id!, true);

        examActionService.loadInitialActions(exam);
        expect(spy).not.toHaveBeenCalled();
        expect(examActionService.initialActionsLoaded).toEqual(initialActionsLoaded);
    });

    // updated cached by timestamp
    it.each(createActions())('should update cached actions', (action: ExamAction) => {
        action.timestamp = dayjs();
        action.ceiledTimestamp = ceilDayjsSeconds(action.timestamp, 15);
        action.examActivityId = 1;

        const actionsPerTimestamp = examActionService.cachedExamActionsGroupedByTimestamp.get(exam.id!) ?? new Map();
        const actionsPerTimestampAndCategory = examActionService.cachedExamActionsGroupedByTimestampAndCategory.get(exam.id!) ?? new Map();
        const lastActionPerStudent = examActionService.cachedLastActionPerStudent.get(exam.id!) ?? new Map();
        const navigatedToPerStudent = examActionService.cachedNavigationsPerStudent.get(exam.id!) ?? new Map();
        const submittedPerStudent = examActionService.cachedSubmissionsPerStudent.get(exam.id!) ?? new Map();

        const increaseActionByTimestampSpy = jest.spyOn(examActionService, 'increaseActionByTimestamp');
        const increaseActionByTimestampAndCategorySpy = jest.spyOn(examActionService, 'increaseActionByTimestampAndCategory');
        const updateLastActionPerStudentSpy = jest.spyOn(examActionService, 'updateLastActionPerStudent');
        const updateNavigationsPerStudentSpy = jest.spyOn(examActionService, 'updateNavigationsPerStudent');
        const updateSubmissionsPerStudentSpy = jest.spyOn(examActionService, 'updateSubmissionsPerStudent');

        examActionService.updateCachedActions(exam, [action]);

        expect(increaseActionByTimestampSpy).toHaveBeenCalledOnce();
        actionsPerTimestamp.set(action.ceiledTimestamp.toString(), 1);
        expect(increaseActionByTimestampSpy).toHaveBeenCalledWith(action.ceiledTimestamp.toString(), actionsPerTimestamp);

        expect(increaseActionByTimestampAndCategorySpy).toHaveBeenCalledOnce();
        const categories = new Map();
        Object.keys(ExamActionType).forEach((type) => {
            categories!.set(type, 0);
        });
        categories.set(action.type, 1);
        actionsPerTimestampAndCategory.set(action.ceiledTimestamp.toString(), categories);
        expect(increaseActionByTimestampAndCategorySpy).toHaveBeenCalledWith(action.ceiledTimestamp.toString(), action, actionsPerTimestampAndCategory);

        expect(updateLastActionPerStudentSpy).toHaveBeenCalledOnce();
        lastActionPerStudent.set(action.examActivityId, action);
        expect(updateLastActionPerStudentSpy).toHaveBeenCalledWith(action, lastActionPerStudent);

        expect(updateNavigationsPerStudentSpy).toHaveBeenCalledOnce();
        if (action.type === ExamActionType.SWITCHED_EXERCISE) {
            navigatedToPerStudent.set(action.examActivityId, new Set([(action as SwitchedExerciseAction).exerciseId]));
        }
        expect(updateNavigationsPerStudentSpy).toHaveBeenCalledWith(action, navigatedToPerStudent);

        expect(updateSubmissionsPerStudentSpy).toHaveBeenCalledOnce();
        if (action.type === ExamActionType.SAVED_EXERCISE) {
            submittedPerStudent.set(action.examActivityId, new Set([(action as SavedExerciseAction).exerciseId]));
        }
        expect(updateSubmissionsPerStudentSpy).toHaveBeenCalledWith(action, submittedPerStudent);

        expect(examActionService.cachedExamActionsGroupedByTimestamp).toEqual(new Map([[exam.id!, actionsPerTimestamp]]));
        expect(examActionService.cachedExamActionsGroupedByTimestampAndCategory).toEqual(new Map([[exam.id!, actionsPerTimestampAndCategory]]));
        expect(examActionService.cachedLastActionPerStudent).toEqual(new Map([[exam.id!, lastActionPerStudent]]));
        expect(examActionService.cachedNavigationsPerStudent).toEqual(new Map([[exam.id!, navigatedToPerStudent]]));
        expect(examActionService.cachedSubmissionsPerStudent).toEqual(new Map([[exam.id!, submittedPerStudent]]));
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

    // Send actions
    it.each(createActions())('should call websocket send', (action: ExamAction) => {
        const spy = jest.spyOn(websocketService, 'send');

        examActionService.sendAction(action, exam.id!);

        expect(spy).toHaveBeenCalledOnce();
        expect(spy).toHaveBeenCalledWith(EXAM_MONITORING_ACTIONS_TOPIC(exam.id!), action);
    });

    // open exam monitoring websocket subscription if not existing
    it.each(createActions())('should open exam monitoring websocket subscription if not existing', (action: ExamAction) => {
        const topic = EXAM_MONITORING_ACTION_TOPIC(exam.id!);
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockImplementation(() => {});
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(of(action));
        const updateCachedActionsSpy = jest.spyOn(examActionService, 'updateCachedActions');

        examActionService.openExamMonitoringWebsocketSubscriptionIfNotExisting(exam);

        expect(examActionService.openExamMonitoringWebsocketSubscriptions).toEqual(new Map([[exam.id, topic]]));

        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(topic);

        expect(receiveSpy).toHaveBeenCalledOnce();
        expect(receiveSpy).toHaveBeenCalledWith(topic);

        expect(updateCachedActionsSpy).toHaveBeenCalledOnce();
        expect(updateCachedActionsSpy).toHaveBeenCalledWith(exam, [action]);
    });

    // open exam monitoring update websocket subscription if not existing
    it.each([true, false])('should open exam monitoring update websocket subscription if not existing', (status: boolean) => {
        const topic = EXAM_MONITORING_STATUS_TOPIC(exam.id!);
        const subscribeSpy = jest.spyOn(websocketService, 'subscribe').mockImplementation(() => {});
        const receiveSpy = jest.spyOn(websocketService, 'receive').mockReturnValue(of(status));
        const notifyExamMonitoringUpdateSubscribersSpy = jest.spyOn(examActionService, 'notifyExamMonitoringUpdateSubscribers');

        examActionService.openExamMonitoringUpdateWebsocketSubscriptionIfNotExisting(exam);

        expect(examActionService.openExamMonitoringStatusWebsocketSubscriptions).toEqual(new Map([[exam.id, topic]]));

        expect(subscribeSpy).toHaveBeenCalledOnce();
        expect(subscribeSpy).toHaveBeenCalledWith(topic);

        expect(receiveSpy).toHaveBeenCalledOnce();
        expect(receiveSpy).toHaveBeenCalledWith(topic);

        expect(notifyExamMonitoringUpdateSubscribersSpy).toHaveBeenCalledOnce();
        expect(notifyExamMonitoringUpdateSubscribersSpy).toHaveBeenCalledWith(exam, status);
    });

    // topics
    it('should get correct exam monitoring action topic', () => {
        expect(EXAM_MONITORING_ACTION_TOPIC(exam.id!)).toBe(`/topic/exams/${exam.id!}/exam-live-statistics-action`);
    });

    it('should get correct exam monitoring actions topic', () => {
        expect(EXAM_MONITORING_ACTIONS_TOPIC(exam.id!)).toBe(`/topic/exams/${exam.id!}/exam-live-statistics-actions`);
    });

    it('should get correct exam monitoring status topic', () => {
        expect(EXAM_MONITORING_STATUS_TOPIC(exam.id!)).toBe(`/topic/exams/${exam.id!}/exam-live-statistics-update`);
    });
});
