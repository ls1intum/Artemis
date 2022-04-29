import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActionDetail, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    public resourceUrl = SERVER_API_URL + 'TODO';

    constructor(private serverDateService: ArtemisServerDateService, private http: HttpClient) {}

    public handleActionEvent(studentExam: StudentExam, examActionDetail: ExamActionDetail) {
        const examActivity = studentExam.examActivity ?? new ExamActivity();
        const timestamp = this.serverDateService.now();
        console.log(`Exam activity ${examActivity.examActions.length + 1} with details ${examActionDetail.examActionEvent.toString()}`);

        // TODO: Add validation + ID
        examActivity.addAction(new ExamAction(timestamp, examActionDetail));
    }

    public syncActions(examActions: ExamAction[]): Observable<HttpResponse<void>> {
        return this.http.put<void>(`${this.resourceUrl}/TODO`, examActions, { observe: 'response' });
    }
}
