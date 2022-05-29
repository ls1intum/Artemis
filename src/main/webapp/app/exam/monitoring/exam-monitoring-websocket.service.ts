import { Injectable } from '@angular/core';
import { BehaviorSubject, tap } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';

// TODO: Add topic
const EXAM_MONITORING_TOPIC = (examId: number) => ``;

export interface IExamMonitoringWebsocketService {}

@Injectable({ providedIn: 'root' })
export class ExamMonitoringWebsocketService implements IExamMonitoringWebsocketService {
    examActionObservables: Map<number, BehaviorSubject<ExamAction | undefined>> = new Map<number, BehaviorSubject<ExamAction | undefined>>();
    // TODO: Store actions grouped by activity in cache
    examActivities: Map<number, ExamActivity[]>;
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Notify all exam action subscribers with the newest exam action provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    private notifyExamActionSubscribers = (exam: Exam, examAction: ExamAction) => {
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
        this.jhiWebsocketService
            .receive(topic)
            .pipe(tap((exmAction: ExamAction) => this.notifyExamActionSubscribers(exam, exmAction)))
            .subscribe();
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
}
