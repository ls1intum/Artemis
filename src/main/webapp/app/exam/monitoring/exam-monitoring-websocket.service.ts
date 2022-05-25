import { Injectable } from '@angular/core';
import { BehaviorSubject, tap } from 'rxjs';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Exam } from 'app/entities/exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { cloneDeep } from 'lodash';

// TODO: Add topic
const EXAM_MONITORING_TOPIC = (examId: number) => ``;

export interface IExamMonitoringWebsocketService {}

@Injectable({ providedIn: 'root' })
export class ParticipationWebsocketService implements IExamMonitoringWebsocketService {
    examObservables: Map<number, BehaviorSubject<Exam | undefined>> = new Map<number, BehaviorSubject<Exam>>();
    examActivityObservables: Map<number, BehaviorSubject<ExamActivity[]>> = new Map<number, BehaviorSubject<ExamActivity[]>>();
    openExamMonitoringWebsocketSubscriptions: Map<number, string> = new Map<number, string>();

    constructor(private jhiWebsocketService: JhiWebsocketService) {}

    /**
     * Notify all exam subscribers with the newest exam provided.
     * @param exam received or updated exam
     */
    private notifyExamSubscribers = (exam: Exam) => {
        const examObservable = this.examObservables.get(exam.id!);
        if (!examObservable) {
            this.examObservables.set(exam.id!, new BehaviorSubject(exam));
        } else {
            examObservable.next(exam);
        }
    };

    /**
     * Notify all exam activity subscribers with the newest exam activity provided.
     * @param exam received or updated exam
     * @param examAction received exam action
     */
    private notifyExamActivitySubscribers = (exam: Exam, examAction: ExamAction) => {
        const examActivityObservable = this.examActivityObservables.get(exam.id!);
        if (!examActivityObservable) {
            // TODO: Handle case
        } else {
            const cachedActivity = cloneDeep(this.examActivityObservables.get(exam.id!)!.value!) as ExamActivity[];
            const examActions = cachedActivity.find((activity) => (activity.id = examAction.examActivityId))!.examActions;
            examActions.push(examAction);
            examActivityObservable.next(cachedActivity);
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
            .pipe(tap((exmAction: ExamAction) => this.notifyExamActivitySubscribers(exam, exmAction)))
            .subscribe();
    }

    /**
     * Subscribing to the exam monitoring.
     *
     * If there is no observable for the exam activities a new one will be created.
     *
     * @param exam the exam to observe
     */
    public subscribeForExamActivities(exam: Exam): BehaviorSubject<ExamActivity[]> {
        this.openExamMonitoringWebsocketSubscriptionIfNotExisting(exam);
        let examActivityObservable = this.examActivityObservables.get(exam.id!)!;
        if (!examActivityObservable) {
            examActivityObservable = new BehaviorSubject<ExamActivity[]>([]);
            this.examActivityObservables.set(exam.id!, examActivityObservable);
        }
        return examActivityObservable;
    }

    /**
     * Unsubscribe from the exam monitoring.
     * @param exam the exam to unsubscribe
     * */
    public unsubscribeForExamActivities(exam: Exam): void {
        const topic = EXAM_MONITORING_TOPIC(exam.id!);
        this.jhiWebsocketService.unsubscribe(topic);
        this.openExamMonitoringWebsocketSubscriptions.delete(exam.id!);
    }
}
