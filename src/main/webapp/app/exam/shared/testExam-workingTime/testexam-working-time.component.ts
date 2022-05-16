import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { round } from 'app/shared/util/utils';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-testexam-working-time',
    templateUrl: './testexam-working-time.component.html',
    providers: [],
})
export class TestexamWorkingTimeComponent implements OnInit {
    @Input() studentExam: StudentExam;

    percentUsedWorkingTime = 0;
    usedWorkingTime = 0;

    ngOnInit() {
        if (this.studentExam.exam!.testExam && this.studentExam.submitted && this.studentExam.workingTime && this.studentExam.startedDate && this.studentExam.submissionDate) {
            const regularExamDuration = this.studentExam.workingTime!;
            this.usedWorkingTime = dayjs(this.studentExam.submissionDate).diff(dayjs(this.studentExam.startedDate), 'seconds');
            if (this.usedWorkingTime > regularExamDuration) {
                this.usedWorkingTime = regularExamDuration;
            }
            this.percentUsedWorkingTime = round((this.usedWorkingTime / regularExamDuration) * 100, 2);
            if (this.percentUsedWorkingTime > 100) {
                this.percentUsedWorkingTime = 100;
            }
        }
    }
}
