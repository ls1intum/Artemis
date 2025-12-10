import { ExternalSubmissionService } from 'app/exercise/external-submission/external-submission.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { User } from 'app/core/user/user.model';
import { EntityResponseType, ResultService } from 'app/exercise/result/result.service';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('External Submission Service', () => {
    let httpMock: HttpTestingController;
    let service: ExternalSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: TranslateService, useClass: MockTranslateService }],
        });
        service = TestBed.inject(ExternalSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('submits a new result to the server correctly', () => {
        const resultService = TestBed.inject(ResultService);
        const convertDateFromServerSpy = jest.spyOn(resultService, 'convertResultResponseDatesFromServer').mockImplementation((param) => param);

        const exercise: ProgrammingExercise = {
            id: 1,
            type: ExerciseType.PROGRAMMING,
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        const user: User = { internal: false, id: 2, login: 'ab12cde' };
        const result: Result = { id: undefined };

        let createResult: EntityResponseType | undefined;
        service.create(exercise, user, result).subscribe((subResult) => (createResult = subResult));
        const req = httpMock.expectOne({ url: `api/assessment/exercises/1/external-submission-results?studentLogin=ab12cde`, method: 'POST' });
        const returned = Object.assign({}, result, { id: 4 });
        req.flush(returned);
        expect(convertDateFromServerSpy).toHaveBeenCalledOnce();
        expect(convertDateFromServerSpy).toHaveBeenCalledWith(createResult);
        expect(createResult).toBeDefined();
        expect(createResult!.body).toEqual(returned);
    });

    it('generates initial manual result correctly', () => {
        const result = service.generateInitialManualResult();
        expect(result).toBeInstanceOf(Result);
        expect(result.completionDate).toBeDefined();
        // a maximum delay of 1s between creation and assertion is unlikely but accurate enough for an assertion of ‘approximately now’ here
        expect(dayjs().diff(result.completionDate, 'ms')).toBeLessThan(1000);
        expect(result.successful).toBeTrue();
        expect(result.score).toBe(100);
        expect(result.rated).toBeTrue();
    });
});
