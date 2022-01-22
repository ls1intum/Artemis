import { Component, Input, OnInit } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { normalWorkingTime } from 'app/exam/participate/exam.utils';

@Component({
    selector: 'jhi-student-exam-working-time',
    templateUrl: './student-exam-working-time.component.html',
    providers: [],
})
export class StudentExamWorkingTimeComponent implements OnInit {
    @Input() studentExam: StudentExam;

    percentDifference = 0;

    ngOnInit() {
        this.calculateDifference();
    }

    /**
     * Calculates the relative difference in whole percent between the regular working time of the exam and the individual working time for the student.
     *
     * E.g., for a regular working time of "1h" and a student working time of "1h30min" the difference is +50.
     * @private
     */
    private calculateDifference(): void {
        const regularExamDuration = normalWorkingTime(this.studentExam.exam!)!;
        this.percentDifference = Math.round((this.studentExam.workingTime! / regularExamDuration - 1.0) * 100);
    }
}
