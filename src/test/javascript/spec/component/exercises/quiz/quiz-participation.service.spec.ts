import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Result } from 'app/entities/result.model';
import { QuizParticipationService } from 'app/exercises/quiz/participate/quiz-participation.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';

describe('Quiz Participation Service', () => {
    let service: QuizParticipationService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(QuizParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        exerciseId = 123;
    });

    it('should submit submission for practice', fakeAsync(() => {
        const mockSubmission = new QuizSubmission();
        const mockResult = new Result();
        mockResult.id = 1;
        mockResult.score = 10;
        service.submitForPractice(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.score).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/submissions/practice` });
        req.flush(mockResult);
        tick();
    }));

    it('should submit for preview', fakeAsync(() => {
        const mockSubmission = new QuizSubmission();
        const mockResult = new Result();
        mockResult.id = 1;
        mockResult.score = 10;
        service.submitForPreview(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.score).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/submissions/preview` });
        req.flush(mockResult);
        tick();
    }));

    it('should submit for live mode', fakeAsync(() => {
        const mockSubmission = new QuizSubmission();
        mockSubmission.id = 1;
        mockSubmission.scoreInPoints = 10;
        service.submitForLiveMode(mockSubmission, exerciseId).subscribe((res) => {
            expect(res.body!.id).toBe(1);
            expect(res.body!.scoreInPoints).toBe(10);
        });

        const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/submissions/live` });
        req.flush(mockSubmission);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
