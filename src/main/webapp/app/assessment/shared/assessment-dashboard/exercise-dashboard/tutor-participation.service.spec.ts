import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TutorParticipationDTO, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExampleSubmissionDTO } from 'app/assessment/shared/entities/example-submission.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('Tutor Participation Service', () => {
    let service: TutorParticipationService;
    let httpMock: HttpTestingController;

    const EXERCISE_ID = 1;
    const TUTOR_ID = 3;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(TutorParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should create a TutorParticipationDTO for an exercise', fakeAsync(() => {
        const dtoReviewed = new TutorParticipationDTO(1, EXERCISE_ID, TutorParticipationStatus.REVIEWED_INSTRUCTIONS, TUTOR_ID);
        service
            .create(EXERCISE_ID)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toMatchObject({
                    exerciseId: EXERCISE_ID,
                    status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS,
                });
            });
        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(dtoReviewed);
        tick();
    }));

    it('should assess ExampleSubmission for an exercise', fakeAsync(() => {
        const dtoTrained = new TutorParticipationDTO(2, EXERCISE_ID, TutorParticipationStatus.TRAINED, TUTOR_ID);
        const exampleDto: ExampleSubmissionDTO = {
            id: 9,
            usedForTutorial: true,
            submissionId: 7,
        };

        service
            .assessExampleSubmission(exampleDto, EXERCISE_ID)
            .pipe(take(1))
            .subscribe((resp) => {
                expect(resp.body).toMatchObject({
                    exerciseId: EXERCISE_ID,
                    status: TutorParticipationStatus.TRAINED,
                });
            });

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(dtoTrained);
        tick();
    }));

    it('should fail from creating a TutorParticipationDTO', () => {
        let capturedError: HttpErrorResponse | undefined;

        service
            .create(EXERCISE_ID)
            .pipe(take(1))
            .subscribe({
                next: () => {
                    throw new Error('expected error');
                },
                error: (err) => (capturedError = err),
            });

        const req = httpMock.expectOne((r) => r.method === 'POST');
        req.flush({ message: 'boom' }, { status: 400, statusText: 'Bad Request' });

        expect(capturedError).toBeInstanceOf(HttpErrorResponse);
        expect(capturedError!.status).toBe(400);
    });
});
