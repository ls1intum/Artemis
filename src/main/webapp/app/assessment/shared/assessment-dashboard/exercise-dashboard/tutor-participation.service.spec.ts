import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { take } from 'rxjs/operators';
import { TutorParticipationDTO } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';

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

    it('should create a TutorParticipationDTO for an exercise', fakeAsync(() => {
        const returnedFromService = new TutorParticipationDTO();
        service
            .create(exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(returnedFromService));

        const req = httpMock.expectOne({ method: 'POST' });
        expect(req.request.body).toBeNull();
        req.flush(returnedFromService);
        tick();
    }));

    it('should assess ExampleSubmission for an exercise', fakeAsync(() => {
        const returnedFromService = new TutorParticipationDTO();
        service
            .assessExampleSubmission(new ExampleSubmission(), exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toEqual(returnedFromService));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
