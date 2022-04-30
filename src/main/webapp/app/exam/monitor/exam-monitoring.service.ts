import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActionDetail, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    public getResourceURL(courseId: number, examId: number): string {
        return `${SERVER_API_URL}api/courses/${courseId}/exams/${examId}`;
    }

    constructor(private serverDateService: ArtemisServerDateService, private http: HttpClient) {}

    public handleActionEvent(studentExam: StudentExam, examActionDetail: ExamActionDetail) {
        const examActivity = studentExam.examActivity ?? new ExamActivity();
        const timestamp = this.serverDateService.now();
        console.log(`Exam activity with details ${examActionDetail.examActionEvent.toString()}`);
        examActivity.addAction(new ExamAction(timestamp, examActionDetail));
    }

    public syncActions(examActions: ExamAction[], courseId: number, examId: number, studentExamId: number): Observable<HttpResponse<void>> {
        const url = this.getResourceURL(courseId, examId) + `/student-exams/${studentExamId}/actions`;
        return this.http.put<void>(url, examActions, { observe: 'response' });
    }
}
