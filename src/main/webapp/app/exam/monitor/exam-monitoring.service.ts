import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { Exam } from 'app/entities/exam.model';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    constructor(private serverDateService: ArtemisServerDateService, private websocketService: JhiWebsocketService) {}

    /**
     * Receives the event and adds a timestamp.
     * @param studentExam current student exam
     * @param examAction performed action
     * @param monitoring true if monitoring is enabled
     */
    public handleActionEvent(studentExam: StudentExam, examAction: ExamAction, monitoring: boolean) {
        if (!monitoring) {
            return;
        }
        try {
            const examActivity = studentExam.examActivity || new ExamActivity();
            examAction.timestamp = this.serverDateService.now();
            examAction.studentExamId = studentExam.id;
            examActivity.addAction(examAction);
            studentExam.examActivity = examActivity;
        } catch (e) {}
    }

    /**
     * Saves the actions to the server and removes them from the activity.
     * @param exam current exam
     * @param studentExam current student exam
     * @param connected true if we have a connection
     */
    public saveActions(exam: Exam, studentExam: StudentExam, connected: boolean) {
        // We synchronize the user actions with the server and then delete them on the client, as they are no longer used
        try {
            if (exam.monitoring && studentExam.examActivity && connected) {
                // This should be in most cases an array with one element
                const actionsToSend = studentExam.examActivity.examActions;
                actionsToSend.forEach((action) => this.sendAction(action, exam.id!));

                // After synchronization, we can delete the actions -> filter in case of new actions during the synchronization
                studentExam.examActivity!.examActions = studentExam.examActivity!.examActions.filter((action) => !actionsToSend.includes(action));
            }
        } catch (e) {}
    }

    /**
     * Receives the event and saves it to the server.
     * @param exam current exam
     * @param studentExam current student exam
     * @param examAction performed action
     * @param connected true if we have a connection
     */
    public handleAndSaveActionEvent(exam: Exam, studentExam: StudentExam, examAction: ExamAction, connected: boolean) {
        this.handleActionEvent(studentExam, examAction, !!exam.monitoring);
        this.saveActions(exam, studentExam, connected);
    }

    /**
     * Returns the websocket topic.
     * @param examId of the current exam
     * @return the websocket topic
     */
    public static buildWebsocketTopic(examId: number): string {
        return `topic/exam-monitoring/${examId}/actions`;
    }

    /**
     * Syncs the collected action to the server.
     * @param examAction performed action
     * @param examId of the current exam
     */
    public sendAction(examAction: ExamAction, examId: number): void {
        const topic = ExamMonitoringService.buildWebsocketTopic(examId);
        this.websocketService.send(topic, examAction);
    }
}
