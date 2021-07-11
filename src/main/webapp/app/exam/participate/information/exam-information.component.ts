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

    normalWorkingTime() {
        if (!this.exam || !this.exam.endDate || !this.exam.startDate) {
            return undefined;
        }
        return this.exam.startDate.diff(this.exam.startDate, 'seconds');
    }

    hasAdditionalWorkingTime() {
        if (!this.exam || !this.exam.endDate || !this.exam.startDate) {
            return undefined;
        }
        if (this.studentExam && this.studentExam.workingTime) {
            const personalEndDate = moment(this.exam.startDate).add(this.studentExam.workingTime, 'seconds');
            return personalEndDate.isAfter(this.exam.endDate);
        }
        return false;
    }

    getAdditionalWorkingTime() {
        const personalEndDate = moment(this.exam.startDate).add(this.studentExam.workingTime, 'seconds');
        return personalEndDate.diff(this.exam.endDate, 'seconds');
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
