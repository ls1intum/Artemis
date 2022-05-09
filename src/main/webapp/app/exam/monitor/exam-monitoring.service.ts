import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Exam } from 'app/entities/exam.model';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    public getResourceURL(courseId: number, examId: number): string {
        return `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}`;
    }

    constructor(private serverDateService: ArtemisServerDateService, private http: HttpClient) {}

    public handleActionEvent(studentExam: StudentExam, examAction: ExamAction, monitoring: boolean) {
        if (!monitoring) {
            return;
        }
        const examActivity = studentExam.examActivity || new ExamActivity();
        examAction.timestamp = this.serverDateService.now();
        examActivity.addAction(examAction);
        studentExam.examActivity = examActivity;
    }

    public saveActions(exam: Exam, studentExam: StudentExam, courseId: number) {
        // We synchronize the user actions with the server and then delete them on the client, as they are no longer used
        if (exam.monitoring && studentExam.examActivity !== undefined) {
            const actionsToSync = studentExam.examActivity.examActions;
            this.syncActions(actionsToSync, courseId, exam.id!, studentExam.id!).subscribe({
                // After successful synchronization we can delete the actions -> filter in case of new actions during the synchronization
                next: () => (studentExam.examActivity!.examActions = studentExam.examActivity!.examActions.filter((action) => !actionsToSync.includes(action))),
                // We do not delete the client actions, because they are not synchronized yet
                error: () => {},
            });
        }
    }

    private syncActions(examActions: ExamAction[], courseId: number, examId: number, studentExamId: number): Observable<HttpResponse<void>> {
        const url = this.getResourceURL(courseId, examId) + `/student-exams/${studentExamId}/actions`;
        return this.http.put<void>(url, examActions, { observe: 'response' });
    }
}
