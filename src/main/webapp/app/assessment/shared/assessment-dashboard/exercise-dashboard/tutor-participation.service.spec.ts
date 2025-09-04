import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TutorParticipationDTO } from 'app/exercise/shared/entities/participation/tutor-participation-dto.model';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';

describe('Tutor Participation Service', () => {
    let service: TutorParticipationService;
    let httpMock: HttpTestingController;

    const exerciseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(TutorParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should create a TutorParticipation for an exercise', fakeAsync(() => {
        const returnedFromService: TutorParticipationDTO = {
            id: 10,
            exerciseId,
            tutorId: 42,
            status: TutorParticipationStatus.REVIEWED_INSTRUCTIONS,
            trainedCount: 0,
        };

        service
            .create(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toBe(returnedFromService));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should assess ExampleSubmission for an exercise', fakeAsync(() => {
        const returnedFromService: TutorParticipationDTO = {
            id: 10,
            exerciseId,
            tutorId: 42,
            status: TutorParticipationStatus.TRAINED,
            trainedCount: 3,
        };

        service
            .assessExampleSubmission(new ExampleSubmission(), exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toBe(returnedFromService));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
