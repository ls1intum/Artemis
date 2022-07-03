import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction, ExamActionType, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';
import { HttpClient } from '@angular/common/http';

const EXAM_MONITORING_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/action`;

export interface IExamActionService {}

@Injectable({ providedIn: 'root' })
export class ExamActionService implements IExamActionService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction[]>> = new Map<number, BehaviorSubject<ExamAction[]>>();
    cachedExamActions: Map<number, ExamAction[]> = new Map<number, ExamAction[]>();
    cachedExamActionsGroupedByTimestamp: Map<number, Map<string, number>> = new Map<number, Map<string, number>>();
    cachedExamActionsGroupedByTimestampAndCategory: Map<number, Map<string, Map<string, number>>> = new Map<number, Map<string, Map<string, number>>>();
    cachedLastActionPerStudent: Map<number, Map<number, ExamAction>> = new Map<number, Map<number, ExamAction>>();
    cachedNavigationsPerStudent: Map<number, Map<number, Set<number | undefined>>> = new Map<number, Map<number, Set<number | undefined>>>();
    cachedSubmissionsPerStudent: Map<number, Map<number, Set<number | undefined>>> = new Map<number, Map<number, Set<number | undefined>>>();
    initialActionsLoaded: Map<number, boolean> = new Map<number, boolean>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService, private http: HttpClient) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examActions received exam actions
     */
    public notifyExamActionSubscribers = (exam: Exam, examActions: ExamAction[]) => {
        // Cache and group actions
        const actionsPerTimestamp = this.cachedExamActionsGroupedByTimestamp.get(exam.id!) ?? new Map();
        const actionsPerTimestampAndCategory = this.cachedExamActionsGroupedByTimestampAndCategory.get(exam.id!) ?? new Map();
        const lastActionPerStudent = this.cachedLastActionPerStudent.get(exam.id!) ?? new Map();
        const navigatedToPerStudent = this.cachedNavigationsPerStudent.get(exam.id!) ?? new Map();
        const submittedPerStudent = this.cachedSubmissionsPerStudent.get(exam.id!) ?? new Map();

        for (const action of examActions) {
            this.prepareAction(action);
            const key = action.ceiledTimestamp!.toString();

            actionsPerTimestamp.set(key, (actionsPerTimestamp.get(key) ?? 0) + 1);

            let categories = actionsPerTimestampAndCategory.get(key);
            if (!categories) {
                categories = new Map();
                Object.keys(ExamActionType).forEach((type) => {
                    categories.set(type, 0);
                });
            }
            categories.set(action.type, categories.get(action.type)! + 1);
            actionsPerTimestampAndCategory.set(key, categories);

            const lastAction = lastActionPerStudent.get(action.studentExamId!);
            if (!lastAction || lastAction.timestamp!.isBefore(action.timestamp!)) {
                lastActionPerStudent.set(action.studentExamId!, action);
            }

            if (action.type === ExamActionType.SWITCHED_EXERCISE) {
                const navigatedTo = navigatedToPerStudent.get(action.studentExamId!) ?? new Set();
                navigatedTo.add((action as SwitchedExerciseAction).exerciseId);
                navigatedToPerStudent.set(action.studentExamId!, navigatedTo);
            }

            if (action.type === ExamActionType.SAVED_EXERCISE) {
                const submitted = submittedPerStudent.get(action.studentExamId!) ?? new Set();
                submitted.add((action as SavedExerciseAction).exerciseId);
                submittedPerStudent.set(action.studentExamId!, submitted);
            }
        }

        this.cachedExamActions.set(exam.id!, [...(this.cachedExamActions.get(exam.id!) ?? []), ...examActions]);
        this.cachedExamActionsGroupedByTimestamp.set(exam.id!, actionsPerTimestamp);
        this.cachedExamActionsGroupedByTimestampAndCategory.set(exam.id!, actionsPerTimestampAndCategory);
        this.cachedLastActionPerStudent.set(exam.id!, lastActionPerStudent);
        this.cachedNavigationsPerStudent.set(exam.id!, navigatedToPerStudent);
        this.cachedSubmissionsPerStudent.set(exam.id!, submittedPerStudent);

        const examActionObservable = this.examActionObservables.get(exam.id!);
        if (!examActionObservable) {
            this.examActionObservables.set(exam.id!, new BehaviorSubject(examActions));
        } else {
            examActionObservable.next(examActions);
        }
    };

    /**
     * Checks if a websocket connection for the exam monitoring to the server already exists.
     * If not a new one will be opened.
     *
     */
    private openExamMonitoringWebsocketSubscriptionIfNotExisting(exam: Exam) {
        const topic = EXAM_MONITORING_TOPIC(exam.id!);
        this.openExamMonitoringWebsocketSubscriptions.set(exam.id!, topic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.receive(topic).subscribe((exmAction: ExamAction) => this.notifyExamActionSubscribers(exam, [exmAction]));
    }

    /**
     * Subscribing to the exam monitoring.
     *
     * If there is no observable for the exam actions a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForLatestExamAction = (exam: Exam): BehaviorSubject<ExamAction[]> => {
        this.openExamMonitoringWebsocketSubscriptionIfNotExisting(exam);
        let examActionObservable = this.examActionObservables.get(exam.id!)!;
        if (!examActionObservable) {
            examActionObservable = new BehaviorSubject<ExamAction[]>([]);
            this.examActionObservables.set(exam.id!, examActionObservable);
        }
        return examActionObservable;
    };

    /**
     * Unsubscribe from the exam monitoring.
     * @param exam the exam to unsubscribe
     * */
    public unsubscribeForExamAction(exam: Exam): void {
        const topic = EXAM_MONITORING_TOPIC(exam.id!);
        this.cachedExamActions.set(exam.id!, []);
        this.cachedExamActionsGroupedByTimestamp.set(exam.id!, new Map());
        this.cachedExamActionsGroupedByTimestampAndCategory.set(exam.id!, new Map());
        this.cachedLastActionPerStudent.set(exam.id!, new Map());
        this.cachedNavigationsPerStudent.set(exam.id!, new Map());
        this.cachedSubmissionsPerStudent.set(exam.id!, new Map());
        this.initialActionsLoaded.delete(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamMonitoringWebsocketSubscriptions.delete(exam.id!);
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
        return `${SERVER_API_URL}api/exam-monitoring/${examId}/load-actions`;
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
}
