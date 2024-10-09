import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient, HttpResponse, provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { EntityResponseType, ResultService } from 'app/exercises/shared/result/result.service';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { Result } from 'app/entities/result.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import {
    FeedbackType,
    NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER,
    STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER,
    SUBMISSION_POLICY_FEEDBACK_IDENTIFIER,
} from 'app/entities/feedback.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import * as Sentry from '@sentry/angular';
// Preliminary mock before import to prevent errors
jest.mock('@sentry/angular', () => {
    const originalModule = jest.requireActual('@sentry/angular');
    return {
        ...originalModule,
        captureException: jest.fn(),
    };
});

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
            { testCase: { testName: 'testBubbleSort' }, detailText: 'lorem ipsum', positive: false, type: FeedbackType.AUTOMATIC },
            { testCase: { testName: 'testMergeSort' }, detailText: 'lorem ipsum', positive: true, type: FeedbackType.AUTOMATIC },
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
            { testCase: { testName: 'testBubbleSort' }, detailText: 'lorem ipsum', positive: false, type: FeedbackType.AUTOMATIC },
            { testCase: { testName: 'testMergeSort' }, detailText: 'lorem ipsum', positive: true, type: FeedbackType.AUTOMATIC },
            { text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER, detailText: 'should not get counted', positive: false, type: FeedbackType.AUTOMATIC },
        ],
        testCaseCount: 2,
        passedTestCaseCount: 1,
        codeIssueCount: 1,
        completionDate: dayjs().add(4, 'hours'),
        score: 50,
    };
    const result5: Result = {
        feedbacks: [{ text: 'Manual feedback', type: FeedbackType.MANUAL }],
        participation: { type: ParticipationType.PROGRAMMING },
        completionDate: dayjs().subtract(5, 'minutes'),
        score: 80,
    };
    const result6: Result = {
        feedbacks: [{ text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'AI feedback', type: FeedbackType.AUTOMATIC }],
        participation: { type: ParticipationType.PROGRAMMING },
        completionDate: dayjs().subtract(5, 'minutes'),
        assessmentType: AssessmentType.AUTOMATIC_ATHENA,
        successful: true,
    };
    const result7: Result = {
        feedbacks: [{ text: NON_GRADED_FEEDBACK_SUGGESTION_IDENTIFIER + 'AI feedback', type: FeedbackType.AUTOMATIC }],
        participation: { type: ParticipationType.PROGRAMMING },
        completionDate: dayjs().subtract(5, 'minutes'),
        assessmentType: AssessmentType.AUTOMATIC_ATHENA,
        successful: false,
    };
    const result8: Result = {
        feedbacks: [],
        participation: { type: ParticipationType.PROGRAMMING },
        completionDate: dayjs().subtract(5, 'minutes'),
        assessmentType: AssessmentType.AUTOMATIC_ATHENA,
        successful: undefined,
    };
    const result9: Result = {
        feedbacks: [],
        participation: { type: ParticipationType.PROGRAMMING },
        completionDate: dayjs().add(5, 'minutes'),
        assessmentType: AssessmentType.AUTOMATIC_ATHENA,
        successful: undefined,
    };

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
            imports: [],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
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
        resultWithPoints2.pointsPerCriterion = new Map(Object.entries({ '1': 20, '2': 30 }).map(([key, value]) => [Number(key), value]));

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

    it('should convert Result Response from Server', () => {
        const emptyResponse: EntityResponseType = new HttpResponse<Result>({ body: undefined });
        const response: EntityResponseType = new HttpResponse<Result>({ body: result1 });
        const converResultsSpy = jest.spyOn(resultService as any, 'convertResultDatesFromServer');
        const result = resultService.convertResultResponseDatesFromServer(emptyResponse);
        expect(result).toBe(emptyResponse);
        expect(converResultsSpy).toHaveBeenCalledTimes(0);
        resultService.convertResultResponseDatesFromServer(response);
        expect(converResultsSpy).toHaveBeenCalledOnce();
    });

    it('should trigger csv download', () => {
        const createASpy = jest.spyOn(document, 'createElement');
        resultService.triggerDownloadCSV(['row1', 'row2'], 'filename');
        expect(createASpy).toHaveBeenCalledOnce();
    });

    describe('getResultString', () => {
        let translateServiceSpy: jest.SpyInstance;

        beforeEach(() => {
            translateServiceSpy = jest.spyOn(translateService, 'instant');
        });

        it('should return correct string for non programming exercise long format', () => {
            expect(resultService.getResultString(modelingResult, modelingExercise)).toBe('artemisApp.result.resultString.nonProgramming');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.nonProgramming', { relativeScore: 42, points: 21 });
        });

        it('should return correct string for non programming exercise short format', () => {
            expect(resultService.getResultString(modelingResult, modelingExercise, true)).toBe('artemisApp.result.resultString.short');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.short', { relativeScore: 42 });
        });

        it.each([true, false])('should return correct string for programming exercise with build failure', (short: boolean) => {
            const expectedProgrammingString = short ? 'artemisApp.result.resultString.programmingShort' : 'artemisApp.result.resultString.programming';
            expect(resultService.getResultString(result1, programmingExercise, short)).toBe(expectedProgrammingString);
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.buildFailed');
            if (short) {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 0,
                    buildAndTestMessage: 'artemisApp.result.resultString.buildFailed',
                });
            } else {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 0,
                    buildAndTestMessage: 'artemisApp.result.resultString.buildFailed',
                    points: 0,
                });
            }
        });

        it.each([true, false])('should return correct string for programming exercise with no tests', (short: boolean) => {
            const expectedProgrammingString = short ? 'artemisApp.result.resultString.programmingShort' : 'artemisApp.result.resultString.programming';
            expect(resultService.getResultString(result2, programmingExercise, short)).toBe(expectedProgrammingString);
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.buildSuccessfulNoTests');
            if (short) {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 20,
                    buildAndTestMessage: 'artemisApp.result.resultString.buildSuccessfulNoTests',
                });
            } else {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 20,
                    buildAndTestMessage: 'artemisApp.result.resultString.buildSuccessfulNoTests',
                    points: 40,
                });
            }
        });

        it.each([true, false])('should return correct string for programming exercise with tests', (short: boolean) => {
            const expectedProgrammingString = short ? 'artemisApp.result.resultString.short' : 'artemisApp.result.resultString.programming';
            expect(resultService.getResultString(result3, programmingExercise, short)).toBe(expectedProgrammingString);
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.buildSuccessfulTests', { numberOfTestsPassed: 1, numberOfTestsTotal: 2 });
            if (short) {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 60,
                });
            } else {
                expect(translateServiceSpy).toHaveBeenCalledWith(expectedProgrammingString, {
                    relativeScore: 60,
                    buildAndTestMessage: 'artemisApp.result.resultString.buildSuccessfulTests',
                    points: 120,
                });
            }
        });

        it('should return correct string for programming exercise with code issues', () => {
            expect(resultService.getResultString(result4, programmingExercise)).toBe('artemisApp.result.resultString.programmingCodeIssues');
            expect(translateServiceSpy).toHaveBeenCalledTimes(2);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.buildSuccessfulTests', { numberOfTestsPassed: 1, numberOfTestsTotal: 2 });
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultString.programmingCodeIssues`, {
                relativeScore: 50,
                buildAndTestMessage: 'artemisApp.result.resultString.buildSuccessfulTests',
                numberOfIssues: 1,
                points: 100,
            });
        });

        it('should return correct string for programming exercise preliminary', () => {
            programmingExercise.assessmentDueDate = dayjs().add(5, 'minutes');

            expect(resultService.getResultString(result5, programmingExercise)).toBe('artemisApp.result.resultString.programming (artemisApp.result.preliminary)');
            expect(translateServiceSpy).toHaveBeenCalledTimes(3);
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.buildSuccessfulNoTests');
            expect(translateServiceSpy).toHaveBeenCalledWith(`artemisApp.result.resultString.programming`, {
                relativeScore: 80,
                buildAndTestMessage: 'artemisApp.result.resultString.buildSuccessfulNoTests',
                points: 160,
            });
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.preliminary');
        });

        it('should return correct string for Athena non graded successful feedback', () => {
            programmingExercise.assessmentDueDate = dayjs().subtract(5, 'minutes');

            expect(resultService.getResultString(result6, programmingExercise)).toBe('artemisApp.result.resultString.automaticAIFeedbackSuccessful');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
        });

        it('should return correct string for Athena non graded unsuccessful feedback', () => {
            programmingExercise.assessmentDueDate = dayjs().subtract(5, 'minutes');

            expect(resultService.getResultString(result7, programmingExercise)).toBe('artemisApp.result.resultString.automaticAIFeedbackFailed');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
        });

        it('should return correct string for Athena timed out non graded feedback', () => {
            programmingExercise.assessmentDueDate = dayjs().add(5, 'minutes');

            expect(resultService.getResultString(result8, programmingExercise)).toBe('artemisApp.result.resultString.automaticAIFeedbackTimedOut');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.automaticAIFeedbackTimedOut');
        });

        it('should return correct string for in progress Athena non-graded feedback', () => {
            programmingExercise.assessmentDueDate = dayjs().add(5, 'minutes');

            expect(resultService.getResultString(result9, programmingExercise)).toBe('artemisApp.result.resultString.automaticAIFeedbackInProgress');
            expect(translateServiceSpy).toHaveBeenCalledOnce();
            expect(translateServiceSpy).toHaveBeenCalledWith('artemisApp.result.resultString.automaticAIFeedbackInProgress');
        });

        it('reports to Sentry if result or exercise is undefined', () => {
            // Re-mock to get reference because direct import doesn't work here
            const captureExceptionSpy = jest.spyOn(Sentry, 'captureException');
            captureExceptionSpy.mockClear();
            expect(resultService.getResultString(undefined, undefined)).toBe('');
            expect(captureExceptionSpy).toHaveBeenCalledOnce();
            expect(captureExceptionSpy).toHaveBeenCalledWith('Tried to generate a result string, but either the result or exercise was undefined');
        });
    });

    describe('evaluateBadge', () => {
        it('should be calculated correctly for practice mode', () => {
            const participation: StudentParticipation = { testRun: true, type: ParticipationType.STUDENT };
            const result: Result = {};
            expect(ResultService.evaluateBadge(participation, result)).toEqual({
                class: 'bg-secondary',
                text: 'artemisApp.result.practice',
                tooltip: 'artemisApp.result.practiceTooltip',
            });
        });

        it('should be calculated correctly for rated submission', () => {
            const participation: Participation = {};
            const result: Result = { rated: true };
            expect(ResultService.evaluateBadge(participation, result)).toEqual({
                class: 'bg-success',
                text: 'artemisApp.result.graded',
                tooltip: 'artemisApp.result.gradedTooltip',
            });
        });

        it('should be calculated correctly for unrated submission', () => {
            const participation: Participation = {};
            const result: Result = { rated: false };
            expect(ResultService.evaluateBadge(participation, result)).toEqual({
                class: 'bg-info',
                text: 'artemisApp.result.notGraded',
                tooltip: 'artemisApp.result.notGradedTooltip',
            });
        });
    });
});
