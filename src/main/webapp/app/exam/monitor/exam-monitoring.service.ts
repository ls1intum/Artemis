import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

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

    public syncActions(examActions: ExamAction[], courseId: number, examId: number, studentExamId: number): Observable<HttpResponse<void>> {
        const url = this.getResourceURL(courseId, examId) + `/student-exams/${studentExamId}/actions`;
        console.log(examActions);
        return this.http.put<void>(url, examActions, { observe: 'response' });
    }
}
