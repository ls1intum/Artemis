import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { isEmpty, take } from 'rxjs/operators';
import { ArtemisTestModule } from '../test.module';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { TutorParticipation } from 'app/entities/participation/tutor-participation.model';
import { ExampleSubmission } from 'app/entities/example-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';

describe('Rating Service', () => {
    let service: TutorParticipationService;
    let httpMock: HttpTestingController;
    let accountServiceMock: AccountService;

    const exerciseId = 1;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [{ provide: AccountService, useClass: MockAccountService }],
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

    describe('should not delete tutor participation for guided tour', () => {
        const course: Course | undefined = undefined;
        it('if course is undefined', fakeAsync(() => {
            service
                .deleteTutorParticipationForGuidedTour(course!, {} as Exercise)
                .pipe(isEmpty())
                .subscribe();

            tick();
        }));

        it('if user is not at least tutor for the course', fakeAsync(() => {
            jest.spyOn(accountServiceMock, 'isAtLeastTutorInCourse').mockReturnValue(false);

            service
                .deleteTutorParticipationForGuidedTour(new Course(), {} as Exercise)
                .pipe(isEmpty())
                .subscribe();

            tick();
        }));
    });

    it('should delete tutor participation for guided tour if there is a course and is at least turo for the course', fakeAsync(() => {
        jest.spyOn(accountServiceMock, 'isAtLeastTutorInCourse').mockReturnValue(true);

        service
            .deleteTutorParticipationForGuidedTour(new Course(), { id: exerciseId } as Exercise)
            .pipe(take(1))
            .subscribe();

        const req = httpMock.expectOne({ method: 'DELETE' });
        req.flush({});
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
