import { Injectable } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { BehaviorSubject, Observable } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ExamActionService } from 'app/exam/statistics/exam-action.service';
import { captureException } from '@sentry/browser';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import dayjs from 'dayjs/esm';

export const EXAM_LIVE_STATISTICS_UPDATE_URL = (courseId: number, examId: number) => `${SERVER_API_URL}/api/courses/${courseId}/exams/${examId}/statistics`;

@Injectable({ providedIn: 'root' })
export class ExamLiveStatisticsService {
    examObservables: Map<number, BehaviorSubject<Exam | undefined>> = new Map<number, BehaviorSubject<Exam>>();

    constructor(private examActionService: ExamActionService, private serverDateService: ArtemisServerDateService, private http: HttpClient) {}

    /**
     * Notify all exam subscribers with the newest exam provided.
     * @param exam received or updated exam
     */
    public notifyExamSubscribers = (exam: Exam) => {
        const examObservable = this.examObservables.get(exam.id!);
        if (!examObservable) {
            this.examObservables.set(exam.id!, new BehaviorSubject(exam));
        } else {
            examObservable.next(exam);
        }
    };

    /**
     * Get exam as observable
     * @param examId exam to observe
     */
    public getExamBehaviorSubject = (examId: number): BehaviorSubject<Exam | undefined> | undefined => {
        return this.examObservables.get(examId);
    };

    /**
     * Receives the event and adds a timestamp.
     * @param studentExam current student exam
     * @param examAction performed action
     * @param liveStatistics true if live statistics are enabled
     * @param timestamp provided timestamp of the action. Usefully for actions which are async but called earlier.
     */
    public handleActionEvent(studentExam: StudentExam, examAction: ExamAction, liveStatistics: boolean, timestamp?: dayjs.Dayjs) {
        if (!liveStatistics) {
            return;
        }
        try {
            const examActivity = studentExam.examActivity || new ExamActivity();
            examAction.timestamp = timestamp ?? this.serverDateService.now();
            examAction.studentExamId = studentExam.id;
            examActivity.addAction(examAction);
            studentExam.examActivity = examActivity;
        } catch (error) {
            // Send the error to sentry
            captureException(error);
        }
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
            if (exam.liveStatistics && studentExam.examActivity && connected) {
                // This should be in most cases an array with one element
                const actionsToSend = studentExam.examActivity.examActions;
                actionsToSend.forEach((action) => this.examActionService.sendAction(action, exam.id!));

                // After synchronization, we can delete the actions -> filter in case of new actions during the synchronization
                studentExam.examActivity!.examActions = studentExam.examActivity!.examActions.filter((action) => !actionsToSend.includes(action));
            }
        } catch (error) {
            // Send the error to sentry
            captureException(error);
        }
    }

    /**
     * Receives the event and saves it to the server.
     * @param exam current exam
     * @param studentExam current student exam
     * @param examAction performed action
     * @param connected true if we have a connection
     * @param timestamp provided timestamp of the action. Usefully for actions which are async but called earlier.
     */
    public handleAndSaveActionEvent(exam: Exam, studentExam: StudentExam, examAction: ExamAction, connected: boolean, timestamp?: dayjs.Dayjs) {
        this.handleActionEvent(studentExam, examAction, !!exam.liveStatistics, timestamp);
        this.saveActions(exam, studentExam, connected);
    }

    /**
     * Updates the current state of the exam live statistics.
     */
    updateExamLiveStatistics(exam: Exam, liveStatistics: boolean): Observable<HttpResponse<boolean>> {
        return this.http.put<boolean>(EXAM_LIVE_STATISTICS_UPDATE_URL(exam.course?.id!, exam.id!), liveStatistics, { observe: 'response' });
    }
}
