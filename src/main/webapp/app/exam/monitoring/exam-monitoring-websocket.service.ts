import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

const EXAM_MONITORING_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/action`;
const EXAM_MONITORING_USER_TOPIC = (examId: number) => `/user/topic/exam-monitoring/${examId}/action`;
const EXAM_MONITORING_INITIAL_LOAD_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/load-actions`;

export interface IExamMonitoringWebsocketService {}

@Injectable({ providedIn: 'root' })
export class ExamMonitoringWebsocketService implements IExamMonitoringWebsocketService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction | undefined>> = new Map<number, BehaviorSubject<ExamAction | undefined>>();
    cachedExamActions: Map<number, ExamAction[]> = new Map<number, ExamAction[]>();
    initialActionsLoaded: Map<number, boolean> = new Map<number, boolean>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();
    openExamMonitoringUserWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    public notifyExamActionSubscribers = (exam: Exam, examAction: ExamAction) => {
        this.prepareAction(examAction);
        this.cachedExamActions.set(exam.id!, [...(this.cachedExamActions.get(exam.id!) ?? []), examAction]);
        const examActionObservable = this.examActionObservables.get(exam.id!);
        if (!examActionObservable) {
            this.examActionObservables.set(exam.id!, new BehaviorSubject(examAction));
        } else {
            examActionObservable.next(examAction);
        }
    };

    /**
     * Checks if a websocket connection for the exam monitoring to the server already exists.
     * If not a new one will be opened.
     *
     */
    private openExamMonitoringWebsocketSubscriptionIfNotExisting(exam: Exam) {
        const topic = EXAM_MONITORING_TOPIC(exam.id!);
        const userTopic = EXAM_MONITORING_USER_TOPIC(exam.id!);
        this.openExamMonitoringWebsocketSubscriptions.set(exam.id!, topic);
        this.openExamMonitoringUserWebsocketSubscriptions.set(exam.id!, userTopic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.subscribe(userTopic);
        this.jhiWebsocketService.receive(topic).subscribe((exmAction: ExamAction) => this.notifyExamActionSubscribers(exam, exmAction));
        this.jhiWebsocketService.receive(userTopic).subscribe((exmAction: ExamAction) => this.notifyExamActionSubscribers(exam, exmAction));
    }

    /**
     * Subscribing to the exam monitoring.
     *
     * If there is no observable for the exam actions a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForLatestExamAction = (exam: Exam): BehaviorSubject<ExamAction | undefined> => {
        this.openExamMonitoringWebsocketSubscriptionIfNotExisting(exam);
        let examActionObservable = this.examActionObservables.get(exam.id!)!;
        if (!examActionObservable) {
            examActionObservable = new BehaviorSubject<ExamAction | undefined>(undefined);
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
     * Send a message via websockets to the server to receive the initial actions.
     * @param exam exam to which the actions belong
     * */
    public loadInitialActions(exam: Exam): void {
        if (!this.initialActionsLoaded.get(exam.id!)) {
            const topic = EXAM_MONITORING_INITIAL_LOAD_TOPIC(exam.id!);
            this.jhiWebsocketService.send(topic, null);
            this.initialActionsLoaded.set(exam.id!, true);
        }
    }

    /**
     * Returns the exam as observable.
     * @param examId corresponding exam id
     */
    public getExamMonitoringObservable(examId: number): BehaviorSubject<ExamAction | undefined> | undefined {
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
