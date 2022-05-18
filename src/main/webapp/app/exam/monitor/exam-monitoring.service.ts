import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Exam } from 'app/entities/exam.model';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    constructor(private serverDateService: ArtemisServerDateService, private http: HttpClient) {}

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
        const examActivity = studentExam.examActivity || new ExamActivity();
        examAction.timestamp = this.serverDateService.now();
        examActivity.addAction(examAction);
        studentExam.examActivity = examActivity;
    }

    /**
     * Saves the actions to the server and removes them from the activity. If the save operation wasn't successful, the actions are not removed.
     * @param exam current exam
     * @param studentExam current student exam
     * @param courseId which the exam belongs to
     */
    public saveActions(exam: Exam, studentExam: StudentExam, courseId: number) {
        // We synchronize the user actions with the server and then delete them on the client, as they are no longer used
        if (exam.monitoring && studentExam.examActivity) {
            const actionsToSync = studentExam.examActivity.examActions;
            this.syncActions(actionsToSync, courseId, exam.id!, studentExam.id!).subscribe({
                // After successful synchronization we can delete the actions -> filter in case of new actions during the synchronization
                next: () => (studentExam.examActivity!.examActions = studentExam.examActivity!.examActions.filter((action) => !actionsToSync.includes(action))),
                // We do not delete the client actions, because they are not synchronized yet
                error: () => {},
            });
        }
    }

    /**
     * Returns the resource url.
     * @param courseId which the exam belongs to
     * @param examId of the current exam
     * @return the resource url
     */
    public static getResourceURL(courseId: number, examId: number): string {
        return `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}`;
    }

    /**
     * Syncs the collected actions to the server.
     * @param examActions performed actions between the last sync and now
     * @param courseId which the exam belongs to
     * @param examId of the current exam
     * @param studentExamId of the student
     * @return error if the sync was not successful
     */
    public syncActions(examActions: ExamAction[], courseId: number, examId: number, studentExamId: number): Observable<void> {
        const url = ExamMonitoringService.getResourceURL(courseId, examId) + `/student-exams/${studentExamId}/actions`;
        return this.http.put<void>(url, examActions);
    }
}
