import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';
import { HttpClient } from '@angular/common/http';

const EXAM_MONITORING_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/action`;
const EXAM_MONITORING_USER_TOPIC = (examId: number) => `/user/topic/exam-monitoring/${examId}/action`;

export interface IExamActionService {}

@Injectable({ providedIn: 'root' })
export class ExamActionService implements IExamActionService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction[]>> = new Map<number, BehaviorSubject<ExamAction[]>>();
    cachedExamActions: Map<number, ExamAction[]> = new Map<number, ExamAction[]>();
    initialActionsLoaded: Map<number, boolean> = new Map<number, boolean>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();
    openExamMonitoringUserWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService, private http: HttpClient) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    public notifyExamActionSubscribers = (exam: Exam, examActions: ExamAction[]) => {
        const t0 = performance.now();
        examActions.forEach((action) => this.prepareAction(action));
        this.cachedExamActions.set(exam.id!, [...(this.cachedExamActions.get(exam.id!) ?? []), ...examActions]);
        const examActionObservable = this.examActionObservables.get(exam.id!);
        if (!examActionObservable) {
            this.examActionObservables.set(exam.id!, new BehaviorSubject(examActions));
        } else {
            examActionObservable.next(examActions);
        }
        const t1 = performance.now();
        console.log(`Notify exam action subscribers took ${t1 - t0} milliseconds.`);
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
        const userTopic = EXAM_MONITORING_USER_TOPIC(exam.id!);
        this.cachedExamActions.set(exam.id!, []);
        this.initialActionsLoaded.delete(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.jhiWebsocketService.unsubscribe(userTopic);
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
     * Returns the exam as observable.
     * @param examId corresponding exam id
     */
    public getExamMonitoringObservable(examId: number): BehaviorSubject<ExamAction[]> | undefined {
        return this.examActionObservables.get(examId);
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
