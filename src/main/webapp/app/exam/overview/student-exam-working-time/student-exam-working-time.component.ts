import { Component, OnInit, input } from '@angular/core';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { getRelativeWorkingTimeExtension } from 'app/exam/overview/exam.utils';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-student-exam-working-time',
    templateUrl: './student-exam-working-time.component.html',
    providers: [],
    imports: [ArtemisDurationFromSecondsPipe],
})
export class StudentExamWorkingTimeComponent implements OnInit {
    studentExam = input.required<StudentExam>();

    percentDifference = 0;
    isTestRun = false;
    isTestExam = false;

    ngOnInit() {
        this.isTestRun = this.studentExam().testRun ?? false;
        this.isTestExam = this.studentExam().exam?.testExam ?? false;
        const workingTime = this.studentExam().workingTime;
        const exam = this.studentExam().exam;
        if (exam && workingTime && !this.isTestRun && !this.isTestExam) {
            this.percentDifference = getRelativeWorkingTimeExtension(exam, workingTime);
        }
    }
}
