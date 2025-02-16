import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { getRelativeWorkingTimeExtension } from 'app/exam/participate/exam.utils';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-student-exam-working-time',
    templateUrl: './student-exam-working-time.component.html',
    providers: [],
    imports: [ArtemisDurationFromSecondsPipe],
})
export class StudentExamWorkingTimeComponent implements OnInit {
    @Input() studentExam: StudentExam;

    percentDifference = 0;
    isTestRun = false;
    isTestExam = false;

    ngOnInit() {
        this.isTestRun = this.studentExam.testRun ?? false;
        this.isTestExam = this.studentExam.exam?.testExam ?? false;
        if (this.studentExam.exam && this.studentExam.workingTime && !this.isTestRun && !this.isTestExam) {
            this.percentDifference = getRelativeWorkingTimeExtension(this.studentExam.exam, this.studentExam.workingTime);
        }
    }
}
