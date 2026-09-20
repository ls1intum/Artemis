import { Component, OnInit, input, signal } from '@angular/core';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { getRelativeWorkingTimeExtension, isRealExam } from 'app/exam/overview/exam.utils';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-student-exam-working-time',
    templateUrl: './student-exam-working-time.component.html',
    providers: [],
    imports: [ArtemisDurationFromSecondsPipe],
})
export class StudentExamWorkingTimeComponent implements OnInit {
    studentExam = input.required<StudentExam>();

    readonly percentDifference = signal<number>(0);
    readonly isTestRun = signal<boolean>(false);
    readonly isTestExam = signal<boolean>(false);

    ngOnInit() {
        this.isTestRun.set(this.studentExam().testRun ?? false);
        this.isTestExam.set(!isRealExam(this.studentExam().exam));
        const workingTime = this.studentExam().workingTime;
        const exam = this.studentExam().exam;
        if (exam && workingTime && !this.isTestRun() && !this.isTestExam()) {
            this.percentDifference.set(getRelativeWorkingTimeExtension(exam, workingTime));
        }
    }
}
