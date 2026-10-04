import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { QuizParticipationService } from 'app/quiz/overview/service/quiz-participation.service';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';

describe('Quiz Participation Service', () => {
    let service: QuizParticipationService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        service = TestBed.inject(QuizParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseId = 123;
    });

    it('should submit submission for practice', () => {
        const mockSubmission = new QuizSubmission();
        const mockResult = new Result();
        mockResult.id = 1;
        mockResult.score = 10;
        service.submitForPractice(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.score).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/quiz/exercises/${exerciseId}/submissions/practice` });
        req.flush(mockResult);
    });

    describe.each(['practice', 'preview'] as const)('short-answer submissions in %s mode', (mode) => {
        it.each([
            { name: 'partially answered', texts: [' first ', ''], expected: [{ text: ' first ', spotId: 1 }] },
            { name: 'whitespace-only field', texts: ['first', ' \t\n'], expected: [{ text: 'first', spotId: 1 }] },
            { name: 'cleared fields', texts: ['', ''], expected: [] },
            { name: 'only whitespace', texts: [' ', '\t'], expected: [] },
            { name: 'untouched question', texts: [], expected: [] },
            { name: 'undefined text', texts: [undefined], expected: [] },
            {
                name: 'fully answered',
                texts: ['first', 'second'],
                expected: [
                    { text: 'first', spotId: 1 },
                    { text: 'second', spotId: 2 },
                ],
            },
        ])('should submit $name without blank entries', ({ texts, expected }) => {
            const question = new ShortAnswerQuestion();
            question.id = 7;
            const answer = new ShortAnswerSubmittedAnswer();
            answer.quizQuestion = question;
            answer.submittedTexts = texts.map((text, index) => {
                const submittedText = new ShortAnswerSubmittedText();
                submittedText.text = text;
                submittedText.spot = new ShortAnswerSpot();
                submittedText.spot.id = index + 1;
                return submittedText;
            });
            const submission = new QuizSubmission();
            submission.submittedAnswers = [answer];

            service[mode === 'practice' ? 'submitForPractice' : 'submitForPreview'](submission, exerciseId).subscribe();

            const req = httpMock.expectOne({ method: 'POST', url: `api/quiz/exercises/${exerciseId}/submissions/${mode}` });
            expect(req.request.body).toEqual({ submittedAnswers: [{ type: 'short-answer', questionId: 7, submittedTexts: expected }] });
            expect(answer.submittedTexts.map((submittedText) => submittedText.text)).toEqual(texts);
            req.flush(new Result());
        });
    });

    it('should submit for preview', () => {
        const mockSubmission = new QuizSubmission();
        const mockResult = new Result();
        mockResult.id = 1;
        mockResult.score = 10;
        service.submitForPreview(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.score).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/quiz/exercises/${exerciseId}/submissions/preview` });
        req.flush(mockResult);
    });

    it.each([true, false])('should save or submit for live mode', (submit: boolean) => {
        const mockSubmission = new QuizSubmission();
        mockSubmission.id = 1;
        mockSubmission.scoreInPoints = 10;
        service.saveOrSubmitForLiveMode(mockSubmission, exerciseId, submit).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.scoreInPoints).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/quiz/exercises/${exerciseId}/submissions/live?submit=${submit}` });
        req.flush(mockSubmission);
    });

    afterEach(() => {
        httpMock.verify();
    });
});
