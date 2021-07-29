import { Component, Input } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import * as examUtils from 'app/exam/participate/exam-utils';

@Component({
    selector: 'jhi-exam-information',
    templateUrl: './exam-information.component.html',
})
export class ExamInformationComponent {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    endTime() {
        return examUtils.endTime(this.exam, this.studentExam);
    }

    normalWorkingTime(): number | undefined {
        return examUtils.normalWorkingTime(this.exam);
    }

    hasAdditionalWorkingTime(): boolean | undefined {
        return examUtils.hasAdditionalWorkingTime(this.exam, this.studentExam);
    }

    getAdditionalWorkingTime(): number {
        return examUtils.getAdditionalWorkingTime(this.exam, this.studentExam);
    }

    isExamOverMultipleDays(): boolean {
        return examUtils.isExamOverMultipleDays(this.exam, this.studentExam);
    }
}
