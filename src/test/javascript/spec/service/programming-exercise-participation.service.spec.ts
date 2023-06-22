import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of } from 'rxjs';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { Submission } from 'app/entities/submission.model';
import { ArtemisTestModule } from '../test.module';
import { Result } from 'app/entities/result.model';

describe('ProgrammingExerciseParticipation Service', () => {
    let service: ProgrammingExerciseParticipationService;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    const resourceUrl = 'api/programming-exercise-participations/';

    let titleSpy: jest.SpyInstance;
    let accessRightsSpy: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                service = TestBed.inject(ProgrammingExerciseParticipationService);
                httpMock = TestBed.inject(HttpTestingController);
                accountService = TestBed.inject(AccountService);

                titleSpy = jest.spyOn(service, 'sendTitlesToEntityTitleService');
                accessRightsSpy = jest.spyOn(accountService, 'setAccessRightsForExerciseAndReferencedCourse');
            });
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    describe('REST calls', () => {
        it.each([true, false])(
            'getLatestResultWithFeedback',
            fakeAsync((withSubmission: boolean) => {
                const participation = { id: 21, exercise: { id: 321 } };
                const result = { id: 42, rated: true, submission: withSubmission ? ({ id: 43 } as Submission) : undefined, participation } as Result;
                const expected = Object.assign({}, result);

                service.getLatestResultWithFeedback(participation.id, withSubmission).subscribe((resp) => expect(resp).toEqual(expected));

                const expectedURL = `${resourceUrl}${participation.id}/latest-result-with-feedbacks?withSubmission=${withSubmission}`;
                const req = httpMock.expectOne({ method: 'GET', url: expectedURL });
                req.flush(result);
                tick();
                expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
                expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
            }),
        );

        it('getStudentParticipationWithLatestResult', fakeAsync(() => {
            const participation = { id: 42, exercise: { id: 123 } };
            const expected = Object.assign({}, participation);

            service.getStudentParticipationWithLatestResult(participation.id).subscribe((resp) => expect(resp).toEqual(expected));

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}${participation.id}/student-participation-with-latest-result-and-feedbacks` });
            req.flush(participation);
            tick();
            expect(titleSpy).toHaveBeenCalledExactlyOnceWith(participation);
            expect(accessRightsSpy).toHaveBeenCalledExactlyOnceWith(participation.exercise);
        }));

        it('checkIfParticipationHasResult', fakeAsync(() => {
            const participation = { id: 42 };

            service.checkIfParticipationHasResult(participation.id).subscribe((resp) => expect(resp).toBeTrue());

            const req = httpMock.expectOne({ method: 'GET', url: `${resourceUrl}${participation.id}/has-result` });
            req.flush(true);
            tick();
        }));

        it.each([{ participationId: 42, gradedParticipationId: 21 }, { participationId: 42 }])(
            'resetRepository',
            fakeAsync((participationId: number, gradedParticipationId?: number) => {
                let successful = false;
                service.resetRepository(participationId, gradedParticipationId).subscribe(() => (successful = true));

                const expectedURL = `${resourceUrl}${participationId}/reset-repository` + (gradedParticipationId ? `?gradedParrticipationId=${gradedParticipationId}` : '');
                const req = httpMock.expectOne({ method: 'PUT', url: expectedURL });
                req.flush(of({}));
                tick();
                expect(successful).toBeTrue();
            }),
        );
    });
});
