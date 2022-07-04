import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';

describe('ResultService', () => {
    let resultService: ResultService;
    let http: HttpClient;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                ResultService,
                ExerciseService,
                ParticipationService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(SubmissionService),
            ],
        });

        resultService = TestBed.inject(ResultService);
        http = TestBed.inject(HttpClient);
    });

    const rawExerciseReleaseDate = '2020-03-30T12:00:00Z';
    const exerciseReleaseDate = dayjs(rawExerciseReleaseDate);
    const rawExerciseDueDate = '2023-03-30T12:00:00Z';
    const exerciseDueDate = dayjs(rawExerciseDueDate);
    const rawParticipationInitializationDate = '2021-04-27T12:56:34.458Z';
    const participationInitializationDate = dayjs(rawParticipationInitializationDate);
    const rawResultCompletionDate = '2022-04-27T12:56:34.458+04:30';
    const resultCompletionDate = dayjs(rawResultCompletionDate);

    const setupRawDates = (exercise: Exercise, participation: Participation, result: Result) => {
        // @ts-ignore
        exercise.releaseDate = rawExerciseReleaseDate;
        // @ts-ignore
        exercise.dueDate = rawExerciseDueDate;
        // @ts-ignore
        participation.initializationDate = rawParticipationInitializationDate;
        // @ts-ignore
        result.completionDate = rawResultCompletionDate;
    };

    it('should convert the dates and points map when receiving a result with points from the server', fakeAsync(() => {
        const exercise = new ProgrammingExercise(undefined, undefined);
        exercise.id = 34;

        const participation1 = new StudentParticipation();
        participation1.exercise = exercise;
        const participation2 = new StudentParticipation();
        participation2.exercise = exercise;

        const result1 = new Result();
        result1.id = 1;
        result1.participation = participation1;
        const result2 = new Result();
        result2.id = 2;
        result2.participation = participation2;

        const resultWithPoints1 = new ResultWithPointsPerGradingCriterion();
        resultWithPoints1.result = result1;
        resultWithPoints1.totalPoints = 100;
        resultWithPoints1.pointsPerCriterion = new Map();
        const resultWithPoints2 = new ResultWithPointsPerGradingCriterion();
        resultWithPoints2.result = result2;
        resultWithPoints2.totalPoints = 50;
        // @ts-ignore
        resultWithPoints2.pointsPerCriterion = { '1': 20, '2': 30 };

        setupRawDates(exercise, participation1, result1);
        const results = [resultWithPoints1, resultWithPoints2];

        const httpStub = jest.spyOn(http, 'get').mockReturnValue(of({ body: results }));

        resultService.getResultsWithPointsPerGradingCriterion(exercise).subscribe((res) => {
            const resultsWithScores: ResultWithPointsPerGradingCriterion[] = res.body!;
            expect(resultsWithScores).toHaveLength(2);

            const receivedResult1 = resultsWithScores.find((resWithPoints) => resWithPoints.result.id === 1);
            expect(receivedResult1!.result.completionDate).toEqual(resultCompletionDate);
            expect(receivedResult1!.result.participation!.initializationDate).toEqual(participationInitializationDate);
            expect(receivedResult1!.result.participation!.exercise!.releaseDate).toEqual(exerciseReleaseDate);
            expect(receivedResult1!.result.participation!.exercise!.dueDate).toEqual(exerciseDueDate);
            expect(receivedResult1!.result.durationInMinutes).toBe(525330);

            const receivedResult2 = resultsWithScores.find((resWithPoints) => resWithPoints.result.id === 2);
            expect(receivedResult2!.result.completionDate).toBe(undefined);
            const expectedPointsMap = new Map();
            expectedPointsMap.set(1, 20);
            expectedPointsMap.set(2, 30);
            expect(receivedResult2!.pointsPerCriterion).toEqual(expectedPointsMap);
        });

        tick();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith(`api/exercises/${exercise.id}/results-with-points-per-criterion`, expect.anything());
    }));
});
