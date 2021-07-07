import { Component, Input } from '@angular/core';
import * as moment from 'moment';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';

@Component({
    selector: 'jhi-exam-information',
    templateUrl: './exam-information.component.html',
})
export class ExamInformationComponent {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    /**
     * Calculates the end time depending on the individual working time.
     */
    endTime() {
        if (!this.exam || !this.exam.endDate) {
            return undefined;
        }
        if (this.studentExam && this.studentExam.workingTime && this.exam.startDate) {
            return moment(this.exam.startDate).add(this.studentExam.workingTime, 'seconds');
        }
        return this.exam.endDate;
    }

    examOverMultipleDays() {
        if (!this.exam || !this.exam.startDate || !this.exam.endDate) {
            return false;
        }
        let endDate;
        if (this.studentExam && this.studentExam.workingTime && this.exam.startDate) {
            endDate = moment(this.exam.startDate).add(this.studentExam.workingTime, 'seconds');
        } else {
            endDate = this.exam.endDate;
        }

        return endDate.dayOfYear() !== this.exam.startDate.dayOfYear();
    }
}
