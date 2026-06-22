import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Exam, ExamType } from 'app/exam/shared/entities/exam.model';
import { TestExamParticipationMessageService } from 'app/exam/overview/services/test-exam-participation-message.service';

describe('TestExamParticipationMessageService', () => {
    setupTestBed({ zoneless: true });

    let service: TestExamParticipationMessageService;

    beforeEach(() => {
        TestBed.configureTestingModule({});
        service = TestBed.inject(TestExamParticipationMessageService);
    });

    it('should show the generic missing student exam message for real exams', () => {
        const exam = new Exam();
        exam.examType = ExamType.REAL;

        const message = service.getMessageKey(exam, 'noStudentExam');

        expect(message).toBe('artemisApp.examParticipation.noStudentExam');
    });

    it('should show that the student should wait until the simulation exam ends', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST_WITH_SIMULATION;

        const message = service.getMessageKey(exam, 'simulationTestExamAttemptAlreadyExistsBeforePractice');

        expect(message).toBe('artemisApp.examParticipation.testExamAttemptUsedPracticeOpens');
    });

    it('should show that the practice phase is not open based on the server error key', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST_WITH_SIMULATION;

        const message = service.getMessageKey(exam, 'testExamPracticePhaseNotStarted');

        expect(message).toBe('artemisApp.examParticipation.testExamPracticeOpens');
    });

    it('should fall back to no further attempts for an unknown simulation and practice error', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST;

        const message = service.getMessageKey(exam, 'unknown');

        expect(message).toBe('artemisApp.examParticipation.noFurtherAttempts');
    });

    it('should show that the test exam has concluded based on the server error key', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST;

        const message = service.getMessageKey(exam, 'examHasAlreadyEnded');

        expect(message).toBe('artemisApp.examParticipation.testExamConcluded');
    });

    it('should fall back to the generic no further attempts message', () => {
        const exam = new Exam();
        exam.examType = ExamType.TEST;

        const message = service.getMessageKey(exam, 'unknown');

        expect(message).toBe('artemisApp.examParticipation.noFurtherAttempts');
    });
});
