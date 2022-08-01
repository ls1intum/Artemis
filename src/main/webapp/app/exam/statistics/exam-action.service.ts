import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds, getEmptyCategories } from 'app/exam/statistics/charts/live-statistics-chart';
import { HttpClient } from '@angular/common/http';

export const EXAM_LIVE_STATISTICS_ACTION_TOPIC = (examId: number) => `/topic/exams/${examId}/live-statistics-action`;
export const EXAM_LIVE_STATISTICS_ACTIONS_TOPIC = (examId: number) => `/topic/exams/${examId}/live-statistics-actions`;
export const EXAM_LIVE_STATISTICS_STATUS_TOPIC = (examId: number) => `/topic/exams/${examId}/live-statistics-update`;

export interface IExamActionService {}

@Injectable({ providedIn: 'root' })
export class ExamActionService implements IExamActionService {
    cachedExamActions: Map<number, ExamAction[]> = new Map<number, ExamAction[]>();
    cachedExamActionsGroupedByTimestamp: Map<number, Map<string, number>> = new Map<number, Map<string, number>>();
    cachedExamActionsGroupedByTimestampAndCategory: Map<number, Map<string, Map<string, number>>> = new Map<number, Map<string, Map<string, number>>>();
    cachedLastActionPerStudent: Map<number, Map<number, ExamAction>> = new Map<number, Map<number, ExamAction>>();
    cachedNavigationsPerStudent: Map<number, Map<number, Set<number | undefined>>> = new Map<number, Map<number, Set<number | undefined>>>();
    cachedSubmissionsPerStudent: Map<number, Map<number, Set<number | undefined>>> = new Map<number, Map<number, Set<number | undefined>>>();
    initialActionsLoaded: Map<number, boolean> = new Map<number, boolean>();
    openExamLiveStatisticsWebsocketSubscriptions: Map<number, string> = new Map<number, string>();
    examLiveStatisticsStatusObservables: Map<number, BehaviorSubject<boolean>> = new Map<number, BehaviorSubject<boolean>>();
    openExamLiveStatisticsStatusWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService, private http: HttpClient) {}

    /**
     * Update, prepare and evaluate actions and insert into cache.
     * @param exam received or updated exam
     * @param examActions received exam actions
     */
    public updateCachedActions = (exam: Exam, examActions: ExamAction[]) => {
        // Cache and group actions
        const actionsPerTimestamp = this.cachedExamActionsGroupedByTimestamp.get(exam.id!) ?? new Map();
        const actionsPerTimestampAndCategory = this.cachedExamActionsGroupedByTimestampAndCategory.get(exam.id!) ?? new Map();
        const lastActionPerStudent = this.cachedLastActionPerStudent.get(exam.id!) ?? new Map();
        const navigatedToPerStudent = this.cachedNavigationsPerStudent.get(exam.id!) ?? new Map();
        const submittedPerStudent = this.cachedSubmissionsPerStudent.get(exam.id!) ?? new Map();

        for (const action of examActions) {
            this.prepareAction(action);
            const timestamp = action.ceiledTimestamp!.toString();

            this.increaseActionByTimestamp(timestamp, actionsPerTimestamp);
            this.increaseActionByTimestampAndCategory(timestamp, action, actionsPerTimestampAndCategory);
            this.updateLastActionPerStudent(action, lastActionPerStudent);
            this.updateNavigationsPerStudent(action, navigatedToPerStudent);
            this.updateSubmissionsPerStudent(action, submittedPerStudent);
        }

        this.cachedExamActions.set(exam.id!, [...(this.cachedExamActions.get(exam.id!) ?? []), ...examActions]);
        this.cachedExamActionsGroupedByTimestamp.set(exam.id!, actionsPerTimestamp);
        this.cachedExamActionsGroupedByTimestampAndCategory.set(exam.id!, actionsPerTimestampAndCategory);
        this.cachedLastActionPerStudent.set(exam.id!, lastActionPerStudent);
        this.cachedNavigationsPerStudent.set(exam.id!, navigatedToPerStudent);
        this.cachedSubmissionsPerStudent.set(exam.id!, submittedPerStudent);
    };

    /**
     * Increase the actions grouped by timestamp and updates the map.
     * @param timestamp timestamp of the action
     * @param actionsPerTimestamp number of actions per timestamp
     * @private
     */
    public increaseActionByTimestamp(timestamp: string, actionsPerTimestamp: Map<string, number>) {
        actionsPerTimestamp.set(timestamp, (actionsPerTimestamp.get(timestamp) ?? 0) + 1);
    }

    /**
     * Increase the actions grouped by timestamp and category and updates the map.
     * @param timestamp timestamp of the action
     * @param action received action
     * @param actionsPerTimestampAndCategory number of actions per timestamp and category
     * @private
     */
    public increaseActionByTimestampAndCategory(timestamp: string, action: ExamAction, actionsPerTimestampAndCategory: Map<string, Map<string, number>>) {
        const categories = actionsPerTimestampAndCategory.get(timestamp) ?? getEmptyCategories();
        categories.set(action.type, categories.get(action.type)! + 1);
        actionsPerTimestampAndCategory.set(timestamp, categories);
    }

    /**
     * Updates the last action performed by the student.
     * @param action received action
     * @param lastActionPerStudent last action per student
     * @private
     */
    public updateLastActionPerStudent(action: ExamAction, lastActionPerStudent: Map<number, ExamAction>) {
        const lastAction = lastActionPerStudent.get(action.examActivityId!);
        if (!lastAction || lastAction.timestamp!.isBefore(action.timestamp!)) {
            lastActionPerStudent.set(action.examActivityId!, action);
        }
    }

    /**
     * Updates the navigations per student.
     * @param action received action
     * @param navigatedToPerStudent navigations per student
     * @private
     */
    public updateNavigationsPerStudent(action: ExamAction, navigatedToPerStudent: Map<number, Set<number | undefined>>) {
        if (action.type === ExamActionType.SWITCHED_EXERCISE) {
            const navigatedTo = navigatedToPerStudent.get(action.examActivityId!) ?? new Set();
            navigatedTo.add((action as SwitchedExerciseAction).exerciseId);
            navigatedToPerStudent.set(action.examActivityId!, navigatedTo);
        }
    }

    /**
     * Updates the submissions per student.
     * @param action received action
     * @param submittedPerStudent submissions per student
     * @private
     */
    public updateSubmissionsPerStudent(action: ExamAction, submittedPerStudent: Map<number, Set<number | undefined>>) {
        if (action.type === ExamActionType.SAVED_EXERCISE) {
            const submitted = submittedPerStudent.get(action.examActivityId!) ?? new Set();
            submitted.add((action as SavedExerciseAction).exerciseId);
            submittedPerStudent.set(action.examActivityId!, submitted);
        }
    }

    /**
     * Checks if a websocket connection for the exam live statistics to the server already exists.
     * @param exam to receive live statistics
     * If not a new one will be opened.
     *
     */
    public openExamLiveStatisticsWebsocketSubscriptionIfNotExisting(exam: Exam) {
        const topic = EXAM_LIVE_STATISTICS_ACTION_TOPIC(exam.id!);
        this.openExamLiveStatisticsWebsocketSubscriptions.set(exam.id!, topic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.receive(topic).subscribe((exmAction: ExamAction) => this.updateCachedActions(exam, [exmAction]));
    }

    /**
     * Subscribing to the exam live statistics.
     *
     * If there is no observable for the exam actions a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForLatestExamAction = (exam: Exam): void => {
        this.openExamLiveStatisticsWebsocketSubscriptionIfNotExisting(exam);
    };

    /**
     * Unsubscribe from the exam live statistics.
     * @param exam the exam to unsubscribe
     * */
    public unsubscribeForExamAction(exam: Exam): void {
        const topic = EXAM_LIVE_STATISTICS_ACTION_TOPIC(exam.id!);
        this.cachedExamActions.set(exam.id!, []);
        this.cachedExamActionsGroupedByTimestamp.set(exam.id!, new Map());
        this.cachedExamActionsGroupedByTimestampAndCategory.set(exam.id!, new Map());
        this.cachedLastActionPerStudent.set(exam.id!, new Map());
        this.cachedNavigationsPerStudent.set(exam.id!, new Map());
        this.cachedSubmissionsPerStudent.set(exam.id!, new Map());
        this.initialActionsLoaded.delete(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamLiveStatisticsWebsocketSubscriptions.delete(exam.id!);
    }

    /**
     * Notify all exam live statistics update subscribers with the newest exam live statistics status.
     * @param exam received or updated exam
     * @param status whether the live statistics is enabled or not
     */
    public notifyExamLiveStatisticsUpdateSubscribers = (exam: Exam, status: boolean) => {
        const examLiveStatisticsStatusObservable = this.examLiveStatisticsStatusObservables.get(exam.id!);
        if (!examLiveStatisticsStatusObservable) {
            this.examLiveStatisticsStatusObservables.set(exam.id!, new BehaviorSubject(status));
        } else {
            examLiveStatisticsStatusObservable.next(status);
        }
    };

    /**
     * Checks if a websocket connection for the exam live statistics update to the server already exists.
     * If not a new one will be opened.
     *
     */
    public openExamLiveStatisticsUpdateWebsocketSubscriptionIfNotExisting(exam: Exam) {
        const topic = EXAM_LIVE_STATISTICS_STATUS_TOPIC(exam.id!);
        this.openExamLiveStatisticsStatusWebsocketSubscriptions.set(exam.id!, topic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.receive(topic).subscribe((status: boolean) => this.notifyExamLiveStatisticsUpdateSubscribers(exam, status));
    }

    /**
     * Subscribing to the exam live statistics update.
     *
     * If there is no observable for the exam live statistics update a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForExamLiveStatisticsUpdate = (exam: Exam): BehaviorSubject<Boolean> => {
        this.openExamLiveStatisticsUpdateWebsocketSubscriptionIfNotExisting(exam);
        let examLiveStatisticsStatusObservable = this.examLiveStatisticsStatusObservables.get(exam.id!)!;
        if (!examLiveStatisticsStatusObservable) {
            examLiveStatisticsStatusObservable = new BehaviorSubject<boolean>(exam.liveStatistics!);
            this.examLiveStatisticsStatusObservables.set(exam.id!, examLiveStatisticsStatusObservable);
        }
        return examLiveStatisticsStatusObservable;
    };

    /**
     * Unsubscribe from the exam live statistics update.
     * @param exam the exam to unsubscribe
     * */
    public unsubscribeForExamLiveStatisticsUpdate(exam: Exam): void {
        const topic = EXAM_LIVE_STATISTICS_STATUS_TOPIC(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamLiveStatisticsStatusWebsocketSubscriptions.delete(exam.id!);
    }

    /**
     * Loads the initial exam actions.
     * @param exam exam to which the actions belong
     * */
    public loadInitialActions(exam: Exam): Observable<ExamAction[]> {
        if (!this.initialActionsLoaded.get(exam.id!)) {
            this.initialActionsLoaded.set(exam.id!, true);
            return this.http.get<ExamAction[]>(ExamActionService.loadActionsEndpoint(exam.id!));
        }
        return of([]);
    }

    /**
     * Returns the load actions endpoint.
     * @param examId of the current exam
     * @return the load actions endpoint
     */
    public static loadActionsEndpoint(examId: number): string {
        return `${SERVER_API_URL}api/exams/${examId}/load-actions`;
    }

    /**
     * Prepares the received actions, e.g. updates the timestamp and creates the rounded timestamp
     * @param examAction received exam action
     * @private
     */
    public prepareAction(examAction: ExamAction) {
        examAction.timestamp = dayjs(examAction.timestamp);
        examAction.ceiledTimestamp = ceilDayjsSeconds(examAction.timestamp, 15);
    }

    /**
     * Syncs the collected action to the server.
     * @param examAction performed action
     * @param examId of the current exam
     */
    public sendAction(examAction: ExamAction, examId: number): void {
        const topic = EXAM_LIVE_STATISTICS_ACTIONS_TOPIC(examId);
        this.jhiWebsocketService.send(topic, examAction);
    }
}
