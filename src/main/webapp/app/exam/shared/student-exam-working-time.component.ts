import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { getRelativeWorkingTimeExtension } from 'app/exam/participate/exam.utils';

@Component({
    selector: 'jhi-student-exam-working-time',
    templateUrl: './student-exam-working-time.component.html',
    providers: [],
})
export class StudentExamWorkingTimeComponent implements OnInit {
    @Input() studentExam: StudentExam;

    percentDifference = 0;
    isTestRun = false;

    ngOnInit() {
        if (this.studentExam.exam && this.studentExam.workingTime) {
            this.percentDifference = getRelativeWorkingTimeExtension(this.studentExam.exam, this.studentExam.workingTime);
        }
        this.isTestRun = this.studentExam.testRun ?? false;
    }
}
