import { Component, effect, input, signal } from '@angular/core';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam, hasTestExamType } from 'app/exam/shared/entities/exam.model';
import { endTime, examWorkingTime, getAdditionalWorkingTime, isExamOverMultipleDays } from 'app/exam/overview/exam.utils';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { TestExamWorkingTimeComponent } from 'app/exam/overview/testExam-workingTime/test-exam-working-time.component';
import dayjs from 'dayjs/esm';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/foundation/pipes/artemis-duration-from-seconds.pipe';
import { ExamModeBadgeComponent } from 'app/exam/shared/exam-mode-badge/exam-mode-badge.component';

@Component({
    selector: 'jhi-exam-general-information',
    styleUrls: ['./exam-general-information.component.scss'],
    templateUrl: './exam-general-information.component.html',
    imports: [
        TranslateDirective,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisDurationFromSecondsPipe,
        ExamModeBadgeComponent,
    ],
})
export class ExamGeneralInformationComponent {
    readonly exam = input<Exam>(undefined!);
    readonly studentExam = input<StudentExam>(undefined!);
    readonly reviewIsOpen = input(false);

    /**
     * The exam cover will contain e.g. the number of exercises which is hidden in the exam summary as
     * the information is shown in the {@link ExamResultOverviewComponent}
     */
    readonly displayOnExamCover = input(false);

    readonly examEndDate = signal<dayjs.Dayjs | undefined>(undefined);
    normalWorkingTime?: number;
    additionalWorkingTime?: number;
    readonly isExamOverMultipleDays = signal<boolean>(undefined!);
    readonly isTestExam = signal<boolean | undefined>(undefined);
    readonly currentDate = signal<dayjs.Dayjs | undefined>(undefined);

    constructor() {
        effect(() => {
            const exam = this.exam();
            const studentExam = this.studentExam();
            this.examEndDate.set(endTime(exam, studentExam));
            this.normalWorkingTime = examWorkingTime(exam);
            this.additionalWorkingTime = getAdditionalWorkingTime(exam, studentExam);
            this.isExamOverMultipleDays.set(isExamOverMultipleDays(exam, studentExam));
            this.isTestExam.set(hasTestExamType(exam));
            if (this.isTestExam()) {
                this.currentDate.set(dayjs());
            }
        });
    }
}
