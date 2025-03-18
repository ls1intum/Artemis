import { Component, Input, OnChanges } from '@angular/core';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam/exam.model';
import { endTime, examWorkingTime, getAdditionalWorkingTime, isExamOverMultipleDays } from 'app/exam/overview/exam.utils';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

@Component({
    selector: 'jhi-exam-general-information',
    styleUrls: ['./exam-general-information.component.scss'],
    templateUrl: './exam-general-information.component.html',
    imports: [TranslateDirective, StudentExamWorkingTimeComponent, TestExamWorkingTimeComponent, ArtemisDatePipe, ArtemisTranslatePipe, ArtemisDurationFromSecondsPipe],
})
export class ExamGeneralInformationComponent implements OnChanges {
    @Input() exam: Exam;
    @Input() studentExam: StudentExam;
    @Input() reviewIsOpen = false;

    /**
     * The exam cover will contain e.g. the number of exercises which is hidden in the exam summary as
     * the information is shown in the {@link ExamResultOverviewComponent}
     */
    @Input() displayOnExamCover = false;

    examEndDate?: dayjs.Dayjs;
    normalWorkingTime?: number;
    additionalWorkingTime?: number;
    isExamOverMultipleDays: boolean;
    isTestExam?: boolean;
    currentDate?: dayjs.Dayjs;

    ngOnChanges() {
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
