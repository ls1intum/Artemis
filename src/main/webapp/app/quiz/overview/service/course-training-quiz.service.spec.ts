import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { CourseTrainingQuizService } from './course-training-quiz.service';
import { QuizQuestion } from 'app/quiz/shared/entities/quiz-question.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { SubmittedAnswerAfterEvaluation } from '../course-training/course-training-quiz/SubmittedAnswerAfterEvaluation';
import { SubmittedAnswer } from '../../shared/entities/submitted-answer.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('CourseTrainingQuizService', () => {
    setupTestBed({ zoneless: true });
    let service: CourseTrainingQuizService;
    let httpMock: HttpTestingController;
    let questionId: number;
    let courseId: number;
    let isRated: boolean;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                CourseTrainingQuizService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(CourseTrainingQuizService);
        httpMock = TestBed.inject(HttpTestingController);
        questionId = 123;
        courseId = 1;
        isRated = true;
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should fetch an Array of quiz questions', () => {
        const mockQuestions: QuizQuestion[] = [{ id: 1 } as QuizQuestion];
        service.getQuizQuestions(courseId).subscribe((questions) => {
            expect(questions).toEqual(mockQuestions);
        });
        const req = httpMock.expectOne(`api/quiz/courses/${courseId}/training-questions`);
        expect(req.request.method).toBe('POST');
        req.flush(mockQuestions);
    });

    it('should submit submission for training', () => {
        const mockTrainingAnswer: SubmittedAnswer = [{}] as SubmittedAnswer;
        const mockAnswer = new SubmittedAnswerAfterEvaluation();
        mockAnswer.scoreInPoints = 10;

        service.submitForTraining(mockTrainingAnswer, questionId, courseId, isRated).subscribe((res) => {
            expect(res.body!.scoreInPoints).toBe(10);
        });

        const req = httpMock.expectOne(`api/quiz/courses/${courseId}/training-questions/${questionId}/submit?isRated=true`);
        expect(req.request.method).toBe('POST');
        req.flush(mockAnswer);
    });
});
