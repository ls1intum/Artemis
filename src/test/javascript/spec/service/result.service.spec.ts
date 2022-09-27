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
import { ExerciseType } from 'app/entities/exercise.model';
import { ParticipationType } from 'app/entities/participation/participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, SUBMISSION_POLICY_FEEDBACK_IDENTIFIER } from 'app/entities/feedback.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import * as Sentry from '@sentry/browser';

describe('ResultService', () => {
    let resultService: ResultService;
    let translateService: TranslateService;
    let http: HttpClient;

    const programmingExercise: ProgrammingExercise = {
        id: 34,
        type: ExerciseType.PROGRAMMING,
        releaseDate: dayjs().subtract(5, 'hours'),
        dueDate: dayjs().subtract(1, 'hours'),
        maxPoints: 200,
        assessmentType: AssessmentType.MANUAL,
        assessmentDueDate: dayjs().subtract(1, 'minutes'),
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const participation1: StudentParticipation = { type: ParticipationType.STUDENT, initializationDate: dayjs().subtract(4, 'hours'), exercise: programmingExercise };
    const participation2: StudentParticipation = { type: ParticipationType.STUDENT, exercise: programmingExercise };
    const submission1: ProgrammingSubmission = { buildFailed: true };
    const result1: Result = { id: 1, participation: participation1, completionDate: dayjs().add(1, 'hours'), submission: submission1, score: 0 };
    const result2: Result = { id: 2, participation: participation2, completionDate: dayjs().add(2, 'hours'), score: 20 };
    const result3: Result = {
        feedbacks: [
            { text: 'testBubbleSort', detailText: 'lorem ipsum', positive: false, type: FeedbackType.AUTOMATIC },
            { text: 'testMergeSort', detailText: 'lorem ipsum', positive: true, type: FeedbackType.AUTOMATIC },
            { text: SUBMISSION_POLICY_FEEDBACK_IDENTIFIER, detailText: 'should not get counted', positive: true, type: FeedbackType.AUTOMATIC },
        ],
        testCaseCount: 2,
        passedTestCaseCount: 1,
        codeIssueCount: 0,
        completionDate: dayjs().add(3, 'hours'),
        score: 60,
    };
    const result4: Result = {
        feedbacks: [
            { text: 'testBubbleSort', detailText: 'lorem ipsum', positive: false, type: FeedbackType.AUTOMATIC },
            { text: 'testMergeSort', detailText: 'lorem ipsum', positive: true, type: FeedbackType.AUTOMATIC },
            { text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, detailText: 'should not get counted', positive: false, type: FeedbackType.AUTOMATIC },
        ],
        testCaseCount: 2,
        passedTestCaseCount: 1,
        codeIssueCount: 1,
        completionDate: dayjs().add(4, 'hours'),
        score: 50,
    };
    const result5: Result = { feedbacks: [{ text: 'Manual feedback', type: FeedbackType.MANUAL }], completionDate: dayjs().subtract(5, 'minutes'), score: 80 };

    const modelingExercise: ModelingExercise = {
        maxPoints: 50,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const modelingParticipation: StudentParticipation = { type: ParticipationType.STUDENT, exercise: modelingExercise };
    const modelingResult: Result = { participation: modelingParticipation, score: 42 };

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
        translateService = TestBed.inject(TranslateService);
        http = TestBed.inject(HttpClient);
    });

    it('should convert the dates and points map when receiving a result with points from the server', fakeAsync(() => {
        const resultWithPoints1 = new ResultWithPointsPerGradingCriterion();
        resultWithPoints1.result = result1;
        resultWithPoints1.totalPoints = 100;
        resultWithPoints1.pointsPerCriterion = new Map();
        const resultWithPoints2 = new ResultWithPointsPerGradingCriterion();
        resultWithPoints2.result = result2;
        resultWithPoints2.totalPoints = 50;
        // @ts-ignore
        resultWithPoints2.pointsPerCriterion = { '1': 20, '2': 30 };

        const results = [resultWithPoints1, resultWithPoints2];

        const httpStub = jest.spyOn(http, 'get').mockReturnValue(of({ body: results }));

        resultService.getResultsWithPointsPerGradingCriterion(programmingExercise).subscribe((res) => {
            const resultsWithScores: ResultWithPointsPerGradingCriterion[] = res.body!;
            expect(resultsWithScores).toHaveLength(2);

            const receivedResult1 = resultsWithScores.find((resWithPoints) => resWithPoints.result.id === 1);
            expect(receivedResult1!.result.completionDate).toEqual(result1.completionDate);
            expect(receivedResult1!.result.participation!.initializationDate).toEqual(participation1.initializationDate);
            expect(receivedResult1!.result.participation!.exercise!.releaseDate).toEqual(programmingExercise.releaseDate);
            expect(receivedResult1!.result.participation!.exercise!.dueDate).toEqual(programmingExercise.dueDate);
            expect(receivedResult1!.result.durationInMinutes).toBe(300);

            const receivedResult2 = resultsWithScores.find((resWithPoints) => resWithPoints.result.id === 2);
            expect(receivedResult2!.result.completionDate).toBe(result2.completionDate);
            const expectedPointsMap = new Map();
            expectedPointsMap.set(1, 20);
            expectedPointsMap.set(2, 30);
            expect(receivedResult2!.pointsPerCriterion).toEqual(expectedPointsMap);
        });

        tick();

        expect(httpStub).toHaveBeenCalledOnce();
        expect(httpStub).toHaveBeenCalledWith(`api/exercises/${programmingExercise.id}/results-with-points-per-criterion`, expect.anything());
    }));

    describe('getResultString', () => {
        let translateServiceSpy: jest.SpyInstance;

        beforeEach(() => {
            translateServiceSpy = jest.spyOn(translateService, 'instant');
        });

        it('should return correct string for non programming exercise', () => {
            expect(resultService.getResultString(modelingResult, modelingExercise)).toBe('artemisApp.result.resultStringNonProgramming');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringNonProgramming', { relativeScore: 42, points: 21 });
        });

        it('should return correct string for programming exercise with build failure', () => {
            expect(resultService.getResultString(result1, programmingExercise)).toBe('artemisApp.result.resultStringProgramming');
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringBuildFailed');
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultStringProgramming`, {
                relativeScore: 0,
                buildAndTestMessage: 'artemisApp.result.resultStringBuildFailed',
                points: 0,
            });
        });

        it('should return correct string for programming exercise with no tests', () => {
            expect(resultService.getResultString(result2, programmingExercise)).toBe('artemisApp.result.resultStringProgramming');
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringBuildSuccessfulNoTests');
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultStringProgramming`, {
                relativeScore: 20,
                buildAndTestMessage: 'artemisApp.result.resultStringBuildSuccessfulNoTests',
                points: 40,
            });
        });

        it('should return correct string for programming exercise with tests', () => {
            expect(resultService.getResultString(result3, programmingExercise)).toBe('artemisApp.result.resultStringProgramming');
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringBuildSuccessfulTests', { numberOfTestsPassed: 1, numberOfTestsTotal: 2 });
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultStringProgramming`, {
                relativeScore: 60,
                buildAndTestMessage: 'artemisApp.result.resultStringBuildSuccessfulTests',
                points: 120,
            });
        });

        it('should return correct string for programming exercise with code issues', () => {
            expect(resultService.getResultString(result4, programmingExercise)).toBe('artemisApp.result.resultStringProgrammingCodeIssues');
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringBuildSuccessfulTests', { numberOfTestsPassed: 1, numberOfTestsTotal: 2 });
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultStringProgrammingCodeIssues`, {
                relativeScore: 50,
                buildAndTestMessage: 'artemisApp.result.resultStringBuildSuccessfulTests',
                numberOfIssues: 1,
                points: 100,
            });
        });

        it('should return correct string for programming exercise preliminary', () => {
            programmingExercise.assessmentDueDate = dayjs().add(5, 'minutes');

            expect(resultService.getResultString(result5, programmingExercise)).toBe('artemisApp.result.resultStringProgramming (artemisApp.result.preliminary)');
            expect(translateServiceSpy).toHaveBeenCalledTimes(3);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultStringBuildSuccessfulNoTests');
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultStringProgramming`, {
                relativeScore: 80,
                buildAndTestMessage: 'artemisApp.result.resultStringBuildSuccessfulNoTests',
                points: 160,
            });
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.preliminary');
        });

        it('reports to Sentry if result or exercise is undefined', () => {
            const captureExceptionSpy = jest.spyOn(Sentry, 'captureException');
            expect(resultService.getResultString(undefined, undefined)).toBe('');
            expect(captureExceptionSpy).toHaveBeenCalledOnce();
            expect(captureExceptionSpy).toHaveBeenCalledWith('Tried to generate a result string, but either the result or exercise was undefined');
        });
    });
});
