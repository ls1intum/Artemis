import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import dayjs from 'dayjs/esm';
import { ceilDayjsSeconds } from 'app/exam/monitoring/charts/monitoring-chart';

const EXAM_MONITORING_TOPIC = (examId: number) => `/topic/exam-monitoring/${examId}/action`;

export interface IExamMonitoringWebsocketService {}

@Injectable({ providedIn: 'root' })
export class ExamMonitoringWebsocketService implements IExamMonitoringWebsocketService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction | undefined>> = new Map<number, BehaviorSubject<ExamAction | undefined>>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    private notifyExamActionSubscribers = (exam: Exam, examAction: ExamAction) => {
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
    private prepareAction(examAction: ExamAction) {
        examAction.timestamp = dayjs(examAction.timestamp);
        examAction.ceiledTimestamp = ceilDayjsSeconds(examAction.timestamp, 15);
    }
}
