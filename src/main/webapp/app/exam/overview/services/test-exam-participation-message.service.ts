import { Injectable } from '@angular/core';
import { Exam, hasTestExamType } from 'app/exam/shared/entities/exam.model';

const TRANSLATION_KEY_BASE = 'artemisApp.examParticipation.' as const;

@Injectable({ providedIn: 'root' })
export class TestExamParticipationMessageService {
    getMessageKey(exam: Exam | undefined, errorKey?: string): string {
        if (!hasTestExamType(exam)) {
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

    private fullTranslationKey(key: string) {
        return TRANSLATION_KEY_BASE + key;
    }
}
