import { Component, Input } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { endTime, getAdditionalWorkingTime, hasAdditionalWorkingTime, isExamOverMultipleDays, normalWorkingTime } from 'app/exam/participate/exam-utils';

@Component({
    selector: 'jhi-exam-information',
    templateUrl: './exam-information.component.html',
})
export class ExamInformationComponent {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    endTime() {
        return endTime(this.exam, this.studentExam);
    }

    normalWorkingTime(): number | undefined {
        return normalWorkingTime(this.exam);
    }

    hasAdditionalWorkingTime(): boolean | undefined {
        return hasAdditionalWorkingTime(this.exam, this.studentExam);
    }

    getAdditionalWorkingTime(): number {
        return getAdditionalWorkingTime(this.exam, this.studentExam);
    }

    isExamOverMultipleDays(): boolean {
        return isExamOverMultipleDays(this.exam, this.studentExam);
    }
}
