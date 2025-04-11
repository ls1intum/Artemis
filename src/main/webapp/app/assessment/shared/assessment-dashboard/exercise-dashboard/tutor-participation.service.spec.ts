import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { isEmpty, take } from 'rxjs/operators';
import { TutorParticipation } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { MockAccountService } from '../../../../../../../test/javascript/spec/helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';

describe('Rating Service', () => {
    let service: TutorParticipationService;
    let httpMock: HttpTestingController;
    let accountServiceMock: AccountService;

    const exerciseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        });
        service = TestBed.inject(TutorParticipationService);
        httpMock = TestBed.inject(HttpTestingController);
        accountServiceMock = TestBed.inject(AccountService);
    });

    it('should create a TutorParticipation for an exercise', fakeAsync(() => {
        const returnedFromService = new TutorParticipation();
        service
            .create(new TutorParticipation(), exerciseId)
            .pipe(take(1))
            .subscribe((resp) => expect(resp.body).toBe(returnedFromService));

        const req = httpMock.expectOne({ method: 'POST' });
        req.flush(returnedFromService);
        tick();
    }));

    it('should assess ExampleSubmission for an exercise', fakeAsync(() => {
        const returnedFromService = new TutorParticipation();
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
