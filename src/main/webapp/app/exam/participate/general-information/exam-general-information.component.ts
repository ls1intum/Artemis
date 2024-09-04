import { Component, Input, OnChanges } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam/exam.model';
import { endTime, examWorkingTime, getAdditionalWorkingTime, isExamOverMultipleDays } from 'app/exam/participate/exam.utils';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-exam-general-information',
    styleUrls: ['./exam-general-information.component.scss'],
    templateUrl: './exam-general-information.component.html',
})
export class ExamGeneralInformationComponent implements OnChanges {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() reviewIsOpen?: boolean = false;

    /**
     * The exam cover will contain e.g. the number of exercises which is hidden in the exam summary as
     * the information is shown in the {@link ExamResultOverviewComponent}
     */
    @Input() displayOnExamCover?: boolean = false;

    examEndDate?: dayjs.Dayjs;
    normalWorkingTime?: number;
    additionalWorkingTime?: number;
    isExamOverMultipleDays: boolean;
    isTestExam?: boolean;
    currentDate?: dayjs.Dayjs;

    ngOnChanges(): void {
        this.examEndDate = endTime(this.exam, this.studentExam);
        this.normalWorkingTime = examWorkingTime(this.exam);
        this.additionalWorkingTime = getAdditionalWorkingTime(this.exam, this.studentExam);
        this.isExamOverMultipleDays = isExamOverMultipleDays(this.exam, this.studentExam);
        this.isTestExam = this.exam?.testExam;
        if (this.isTestExam) {
            this.currentDate = dayjs();
        }
    }
}
