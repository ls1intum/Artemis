import { Injectable, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

export interface TestExamParticipationMessage {
    translationKey: string;
    translateValues: { date?: string };
}

const TRANSLATION_KEY_BASE = 'artemisApp.examParticipation.' as const;

@Injectable({ providedIn: 'root' })
export class TestExamParticipationMessageService {
    private translateService = inject(TranslateService);

    getMessage(exam: Exam | undefined, errorKey?: string): TestExamParticipationMessage {
        const translationKey = this.getMessageKey(exam, errorKey);
        return {
            translationKey,
            translateValues: this.getTranslateValues(exam, translationKey),
        };
    }

    private getMessageKey(exam: Exam | undefined, errorKey?: string): string {
        if (!exam?.testExam) {
            return this.fullTranslationKey('noStudentExam');
        }
        switch (errorKey) {
            case 'simulationTestExamAttemptAlreadyExists':
                return this.fullTranslationKey('testExamAttemptUsed');
            case 'simulationTestExamAttemptAlreadyExistsBeforePractice':
                return this.fullTranslationKey('testExamAttemptUsedPracticeOpens');
            case 'examHasAlreadyEnded':
            case 'simulationTestExamPhaseEnded':
                return this.fullTranslationKey('testExamConcluded');
            case 'testExamPracticePhaseNotStarted':
                return this.fullTranslationKey('testExamPracticeOpens');
        }
        return this.fullTranslationKey('noFurtherAttempts');
    }

    private getTranslateValues(exam: Exam | undefined, translationKey: string): { date?: string } {
        if (translationKey !== this.fullTranslationKey('testExamPracticeOpens') && translationKey !== this.fullTranslationKey('testExamAttemptUsedPracticeOpens')) {
            return {};
        }
        const practiceStartDate = this.getPracticeStartDate(exam);
        return practiceStartDate ? { date: practiceStartDate.format(ArtemisDatePipe.format(this.translateService.getCurrentLang())) } : {};
    }

    private getPracticeStartDate(exam: Exam | undefined): dayjs.Dayjs | undefined {
        if (exam?.testExamPracticeStartDate === undefined) {
            return undefined;
        }
        return dayjs(exam.testExamPracticeStartDate);
    }

    private fullTranslationKey(key: string) {
        return TRANSLATION_KEY_BASE + key;
    }
}
