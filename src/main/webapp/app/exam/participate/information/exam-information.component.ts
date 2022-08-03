import { Component, OnInit, Input } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { endTime, getAdditionalWorkingTime, isExamOverMultipleDays, normalWorkingTime } from 'app/exam/participate/exam.utils';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-information',
    templateUrl: './exam-information.component.html',
})
export class ExamInformationComponent implements OnInit {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;

    examEndDate?: dayjs.Dayjs;
    normalWorkingTime?: number;
    additionalWorkingTime?: number;
    isExamOverMultipleDays: boolean;
    isTestExam?: boolean;
    currentDate?: dayjs.Dayjs;

    ngOnInit(): void {
        this.examEndDate = endTime(this.exam, this.studentExam);
        this.normalWorkingTime = normalWorkingTime(this.exam);
        this.additionalWorkingTime = getAdditionalWorkingTime(this.exam, this.studentExam);
        this.isExamOverMultipleDays = isExamOverMultipleDays(this.exam, this.studentExam);
        this.isTestExam = this.exam?.testExam;
        if (this.isTestExam) {
            this.currentDate = dayjs();
        }
    }
}
