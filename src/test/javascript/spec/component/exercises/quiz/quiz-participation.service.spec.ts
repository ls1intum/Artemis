import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { QuizParticipationService } from 'app/exercises/quiz/participate/quiz-participation.service';
import { QuizSubmission } from 'app/entities/quiz/quiz-submission.model';
import { Result } from 'app/entities/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../../../helpers/mocks/service/mock-account.service';
import { provideHttpClient } from '@angular/common/http';

describe('Quiz Participation Service', () => {
    let service: QuizParticipationService;
    let httpMock: HttpTestingController;
    let exerciseId: number;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
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

    it.each([true, false])(
        'should save or submit for live mode',
        fakeAsync((submit: boolean) => {
            const mockSubmission = new QuizSubmission();
            mockSubmission.id = 1;
            mockSubmission.scoreInPoints = 10;
            service.saveOrSubmitForLiveMode(mockSubmission, exerciseId, submit).subscribe((res) => {
                expect(res.body!.id).toBe(1);
                expect(res.body!.scoreInPoints).toBe(10);
            });

            const req = httpMock.expectOne({ method: 'POST', url: `api/exercises/${exerciseId}/submissions/live?submit=${submit}` });
            req.flush(mockSubmission);
            tick();
        }),
    );

    afterEach(() => {
        httpMock.verify();
    });
});
