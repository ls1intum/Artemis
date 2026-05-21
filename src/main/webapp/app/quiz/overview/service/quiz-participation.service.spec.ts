import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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

describe('Quiz Participation Service', () => {
    setupTestBed({ zoneless: true });
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
