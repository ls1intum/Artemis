import { TestBed } from '@angular/core/testing';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Exam, ExamType } from 'app/exam/shared/entities/exam.model';
import { TestExamParticipationMessageService } from 'app/exam/overview/services/test-exam-participation-message.service';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TestExamParticipationMessageService', () => {
    setupTestBed({ zoneless: true });

    let service: TestExamParticipationMessageService;
    let serverDateService: ArtemisServerDateService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(TestExamParticipationMessageService);
        serverDateService = TestBed.inject(ArtemisServerDateService);
    });

    it('should show the generic missing student exam message for real exams', () => {
        const exam = new Exam();
        exam.examType = ExamType.REAL;

        const message = service.getMessage(exam, 'noStudentExam');

        expect(message.translationKey).toBe('artemisApp.examParticipation.noStudentExam');
        expect(message.translateValues).toEqual({});
    });

    it('should show that a simulation attempt was already used and when practice opens', () => {
        const now = dayjs();
        const exam = new Exam();
        exam.examType = ExamType.TEST_WITH_SIMULATION;
        exam.startDate = now;
        exam.workingTime = 1800;
        exam.gracePeriod = 0;
        const practiceOpensAt = now.add(30, 'minutes');

        const message = service.getMessage(exam, 'simulationTestExamAttemptAlreadyExistsBeforePractice');

        expect(message.translationKey).toBe('artemisApp.examParticipation.testExamAttemptUsedPracticeOpens');
        expect(message.translateValues).toEqual({ date: practiceOpensAt.format(ArtemisDatePipe.format()) });
    });

    it('should show when the practice phase opens based on the server error key', () => {
        const now = dayjs();
        const exam = new Exam();
        exam.examType = ExamType.TEST_WITH_SIMULATION;
        exam.startDate = now;
        exam.workingTime = 1800;
        const practiceOpensAt = now.add(30, 'minutes');

        const message = service.getMessage(exam, 'testExamPracticePhaseNotStarted');

        expect(message.translationKey).toBe('artemisApp.examParticipation.testExamPracticeOpens');
        expect(message.translateValues).toEqual({ date: practiceOpensAt.format(ArtemisDatePipe.format()) });
    });

    it('should fall back to no further attempts for an unknown simulation and practice error', () => {
        const now = dayjs();
        vi.spyOn(serverDateService, 'now').mockReturnValue(now);
        const exam = new Exam();
        exam.examType = ExamType.TEST;
        exam.startDate = now.subtract(2, 'hours');
        exam.workingTime = 30;
        exam.gracePeriod = 0;

        const message = service.getMessage(exam, 'unknown');

        expect(message.translationKey).toBe('artemisApp.examParticipation.noFurtherAttempts');
        expect(message.translateValues).toEqual({});
    });

    it('should show that the test exam has concluded based on the server error key', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST;

        const message = service.getMessage(exam, 'examHasAlreadyEnded');

        expect(message.translationKey).toBe('artemisApp.examParticipation.testExamConcluded');
        expect(message.translateValues).toEqual({});
    });

    it('should fall back to the generic no further attempts message', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST;

        const message = service.getMessage(exam, 'unknown');

        expect(message.translationKey).toBe('artemisApp.examParticipation.noFurtherAttempts');
        expect(message.translateValues).toEqual({});
    });
});
