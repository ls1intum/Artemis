import { Injectable } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamAction, ExamActionDetail, ExamActivity } from 'app/entities/exam-user-activity.model';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Injectable({ providedIn: 'root' })
export class ExamMonitoringService {
    constructor(private serverDateService: ArtemisServerDateService) {}

    public handleActionEvent(studentExam: StudentExam, examActionDetail: ExamActionDetail) {
        const examActivity = studentExam.examActivity ?? new ExamActivity();
        const timestamp = this.serverDateService.now();
        console.log(`Exam activity ${examActivity.examActions.length + 1} with details ${examActionDetail.examActionEvent.toString()}`);

        // TODO: Add validation + ID
        examActivity.addAction(new ExamAction(timestamp, examActionDetail));
    }
}
