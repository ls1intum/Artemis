import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

const EXAM_MONITORING_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/action`;
const EXAM_MONITORING_STATUS_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/update`;

export interface IExamMonitoringWebsocketService {}

@Injectable({ providedIn: 'root' })
export class ExamMonitoringWebsocketService implements IExamMonitoringWebsocketService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction | undefined>> = new Map<number, BehaviorSubject<ExamAction | undefined>>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();
    examMonitoringStatusObservables: Map<number, BehaviorSubject<boolean>> = new Map<number, BehaviorSubject<boolean>>();
    openExamMonitoringStatusWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    public notifyExamActionSubscribers = (exam: Exam, examAction: ExamAction) => {
        this.prepareAction(examAction);
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
        this.openExamMonitoringWebsocketSubscriptions.set(exam.id!, topic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.receive(topic).subscribe((exmAction: ExamAction) => this.notifyExamActionSubscribers(exam, exmAction));
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
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamMonitoringWebsocketSubscriptions.delete(exam.id!);
    }

    /**
     * Notify all exam monitoring update subscribers with the newest exam monitoring status.
     * @param exam received or updated exam
     * @param status whether the monitoring is enabled or not
     */
    public notifyExamMonitoringUpdateSubscribers = (exam: Exam, status: boolean) => {
        const examMonitoringStatusObservable = this.examMonitoringStatusObservables.get(exam.id!);
        if (!examMonitoringStatusObservable) {
            this.examMonitoringStatusObservables.set(exam.id!, new BehaviorSubject(status));
        } else {
            examMonitoringStatusObservable.next(status);
        }
    };

    /**
     * Checks if a websocket connection for the exam monitoring update to the server already exists.
     * If not a new one will be opened.
     *
     */
    private openExamMonitoringUpdateWebsocketSubscriptionIfNotExisting(exam: Exam) {
        const topic = EXAM_MONITORING_STATUS_TOPIC(exam.id!);
        this.openExamMonitoringStatusWebsocketSubscriptions.set(exam.id!, topic);

        this.jhiWebsocketService.subscribe(topic);
        this.jhiWebsocketService.receive(topic).subscribe((status: boolean) => this.notifyExamMonitoringUpdateSubscribers(exam, status));
    }

    /**
     * Subscribing to the exam monitoring update.
     *
     * If there is no observable for the exam monitoring update a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForExamMonitoringUpdate = (exam: Exam): BehaviorSubject<Boolean> => {
        this.openExamMonitoringUpdateWebsocketSubscriptionIfNotExisting(exam);
        let examMonitoringStatusObservable = this.examMonitoringStatusObservables.get(exam.id!)!;
        if (!examMonitoringStatusObservable) {
            examMonitoringStatusObservable = new BehaviorSubject<boolean>(exam.monitoring!);
            this.examMonitoringStatusObservables.set(exam.id!, examMonitoringStatusObservable);
        }
        return examMonitoringStatusObservable;
    };

    /**
     * Unsubscribe from the exam monitoring update.
     * @param exam the exam to unsubscribe
     * */
    public unsubscribeForExamMonitoringUpdate(exam: Exam): void {
        const topic = EXAM_MONITORING_STATUS_TOPIC(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamMonitoringStatusWebsocketSubscriptions.delete(exam.id!);
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
