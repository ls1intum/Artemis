import { ArtemisTestModule } from '../test.module';
import { ExternalSubmissionService } from 'app/exercises/shared/external-submission/external-submission.service';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { User } from 'app/core/user/user.model';
import { EntityResponseType, ResultService } from 'app/exercises/shared/result/result.service';
import dayjs from 'dayjs';

describe('External Submission Service', () => {
    let httpMock: HttpTestingController;
    let service: ExternalSubmissionService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, HttpClientTestingModule],
        });
        service = TestBed.inject(ExternalSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('submits a new result to the server correctly', () => {
        const resultService = TestBed.inject(ResultService);
        const convertDateFromServerSpy = jest.spyOn(resultService, 'convertDateFromServer').mockImplementation((param) => param);

        const exercise = { id: 1, type: ExerciseType.PROGRAMMING } as Exercise;
        const user = { id: 2, login: 'ab12cde' } as User;
        const result = { id: undefined, resultString: 'fooBar' } as Result;

        let createResult: EntityResponseType | undefined;
        service.create(exercise, user, result).subscribe((subResult) => (createResult = subResult));
        const req = httpMock.expectOne({ url: `${SERVER_API_URL}api/exercises/1/external-submission-results?studentLogin=ab12cde`, method: 'POST' });
        const returned = { ...result, id: 4 };
        req.flush(returned);
        expect(convertDateFromServerSpy).toHaveBeenCalledTimes(1);
        expect(convertDateFromServerSpy).toHaveBeenCalledWith(createResult);
        expect(createResult).not.toBe(undefined);
        expect(createResult!.body).toEqual(returned);
    });

    it('generates initial manual result correctly', () => {
        const result = service.generateInitialManualResult();
        expect(result).toBeInstanceOf(Result);
        expect(result.completionDate).not.toBe(undefined);
        expect(Math.abs(dayjs().diff(result.completionDate, 'ms'))).toBeLessThan(10);
        expect(result.successful).toBe(true);
        expect(result.score).toBe(100);
        expect(result.rated).toBe(true);
    });
});
