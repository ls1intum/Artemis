import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Result } from 'app/entities/result.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { Feedback } from 'app/entities/feedback.model';
import { StudentParticipation, isPracticeMode } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { map, tap } from 'rxjs/operators';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { TranslateService } from '@ngx-translate/core';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { captureException } from '@sentry/angular';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import {
    isAIResultAndFailed,
    isAIResultAndIsBeingProcessed,
    isAIResultAndProcessed,
    isAIResultAndTimedOut,
    isStudentParticipation,
} from 'app/exercises/shared/result/result.utils';
import { CsvDownloadService } from 'app/shared/util/CsvDownloadService';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;
export type ResultsWithPointsArrayResponseType = HttpResponse<ResultWithPointsPerGradingCriterion[]>;

export interface Badge {
    class: string;
    text: string;
    tooltip: string;
}

export interface IResultService {
    find: (resultId: number) => Observable<EntityResponseType>;
    getResultsForExerciseWithPointsPerGradingCriterion: (exerciseId: number, req?: any) => Observable<ResultsWithPointsArrayResponseType>;
    getFeedbackDetailsForResult: (participationId: number, result: Result) => Observable<HttpResponse<Feedback[]>>;
    getResultsWithPointsPerGradingCriterion: (exercise: Exercise) => Observable<ResultsWithPointsArrayResponseType>;
    triggerDownloadCSV: (rows: string[], csvFileName: string) => void;
}

@Injectable({ providedIn: 'root' })
export class ResultService implements IResultService {
    private exerciseResourceUrl = 'api/exercises';
    private resultResourceUrl = 'api/results';
    private participationResourceUrl = 'api/participations';

    private readonly MAX_VALUE_PROGRAMMING_RESULT_INTS = 255; // Size of tinyInt in SQL, that is used to store these values

    constructor(
        private http: HttpClient,
        private translateService: TranslateService,
        private csvDownloadService: CsvDownloadService,
    ) {}

    find(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResultResponseDatesFromServer(res)));
    }

    /**
     * Generates the result string for the given exercise and result.
     * Contains the score, achieved points and if it's a programming exercise the tests and code issues as well
     * If either of the arguments is undefined the error is forwarded to sentry and an empty string is returned
     * @param result the result containing all necessary information like the achieved points
     * @param exercise the exercise where the result belongs to
     * @param short flag that indicates if the resultString should use the short format
     */
    getResultString(result: Result | undefined, exercise: Exercise | undefined, short?: boolean): string {
        if (result && exercise) {
            return this.getResultStringDefinedParameters(result, exercise, short);
        } else {
            captureException('Tried to generate a result string, but either the result or exercise was undefined');
            return '';
        }
    }

    /**
     * Generates the result string for the given exercise and result.
     * Contains the score, achieved points and if it's a programming exercise the tests and code issues as well
     * @param result the result containing all necessary information like the achieved points
     * @param exercise the exercise where the result belongs to
     * @param short flag that indicates if the resultString should use the short format
     */
    private getResultStringDefinedParameters(result: Result, exercise: Exercise, short: boolean | undefined): string {
        const relativeScore = roundValueSpecifiedByCourseSettings(result.score!, getCourseFromExercise(exercise));
        const points = roundValueSpecifiedByCourseSettings((result.score! * exercise.maxPoints!) / 100, getCourseFromExercise(exercise));
        if (exercise.type !== ExerciseType.PROGRAMMING) {
            if (Result.isAthenaAIResult(result)) {
                return this.getResultStringNonProgrammingExerciseWithAIFeedback(result, relativeScore, points, short);
            }
            return this.getResultStringNonProgrammingExercise(relativeScore, points, short);
        } else {
            return this.getResultStringProgrammingExercise(result, exercise as ProgrammingExercise, relativeScore, points, short);
        }
    }

    /**
     * Generates the result string for a text exercise. Contains the score and points
     * @param result the result object
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param short flag that indicates if the resultString should use the short format
     */
    private getResultStringNonProgrammingExerciseWithAIFeedback(result: Result, relativeScore: number, points: number, short: boolean | undefined): string {
        let aiFeedbackMessage: string = '';
        if (result && Result.isAthenaAIResult(result) && result.successful === undefined) {
            return this.translateService.instant('artemisApp.result.resultString.automaticAIFeedbackInProgress');
        }
        aiFeedbackMessage = this.getResultStringNonProgrammingExercise(relativeScore, points, short);
        return `${aiFeedbackMessage} (${this.translateService.instant('artemisApp.result.preliminary')})`;
    }

    /**
     * Generates the result string for a non programming exercise. Contains the score and points
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param short flag that indicates if the resultString should use the short format
     */
    private getResultStringNonProgrammingExercise(relativeScore: number, points: number, short: boolean | undefined): string {
        if (short) {
            return this.translateService.instant(`artemisApp.result.resultString.short`, {
                relativeScore,
            });
        } else {
            return this.translateService.instant(`artemisApp.result.resultString.nonProgramming`, {
                relativeScore,
                points,
            });
        }
    }

    /**
     * Generates the result string for a programming exercise. Contains the score, achieved points and the tests and code issues as well.
     * If the result is a build failure or no tests were executed, the string replaces some parts with a helpful explanation
     * @param result the result containing all necessary information like the achieved points
     * @param exercise the exercise where the result belongs to
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param short flag that indicates if the resultString should use the short format
     */
    private getResultStringProgrammingExercise(result: Result, exercise: ProgrammingExercise, relativeScore: number, points: number, short: boolean | undefined): string {
        let buildAndTestMessage: string;
        if (result.submission && (result.submission as ProgrammingSubmission).buildFailed) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.buildFailed');
        } else if (isAIResultAndFailed(result)) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.automaticAIFeedbackFailed');
        } else if (isAIResultAndIsBeingProcessed(result)) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.automaticAIFeedbackInProgress');
        } else if (isAIResultAndTimedOut(result)) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.automaticAIFeedbackTimedOut');
        } else if (isAIResultAndProcessed(result)) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.automaticAIFeedbackSuccessful');
        } else if (!result.testCaseCount) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.buildSuccessfulNoTests');
        } else {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultString.buildSuccessfulTests', {
                numberOfTestsPassed:
                    result.passedTestCaseCount! >= this.MAX_VALUE_PROGRAMMING_RESULT_INTS ? `${this.MAX_VALUE_PROGRAMMING_RESULT_INTS}+` : result.passedTestCaseCount,
                numberOfTestsTotal: result.testCaseCount! >= this.MAX_VALUE_PROGRAMMING_RESULT_INTS ? `${this.MAX_VALUE_PROGRAMMING_RESULT_INTS}+` : result.testCaseCount,
            });
        }

        let resultString = this.getBaseResultStringProgrammingExercise(result, relativeScore, points, buildAndTestMessage, short);

        if (isStudentParticipation(result) && isResultPreliminary(result, exercise)) {
            resultString += ' (' + this.translateService.instant('artemisApp.result.preliminary') + ')';
        }

        return resultString;
    }

    /**
     * Generates the result string for a programming exercise
     * @param result the result containing all necessary information like the achieved points
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param buildAndTestMessage the string containing information about the build. Either about the build failure or the passed tests
     * @param short flag that indicates if the resultString should use the short format
     */
    private getBaseResultStringProgrammingExercise(result: Result, relativeScore: number, points: number, buildAndTestMessage: string, short: boolean | undefined): string {
        if (Result.isAthenaAIResult(result)) {
            return buildAndTestMessage;
        }
        if (short) {
            if (!result.testCaseCount) {
                return this.translateService.instant('artemisApp.result.resultString.programmingShort', {
                    relativeScore,
                    buildAndTestMessage,
                });
            } else {
                return this.translateService.instant('artemisApp.result.resultString.short', {
                    relativeScore,
                });
            }
        } else if (result.codeIssueCount && result.codeIssueCount > 0) {
            return this.translateService.instant('artemisApp.result.resultString.programmingCodeIssues', {
                relativeScore,
                buildAndTestMessage,
                numberOfIssues: result.codeIssueCount! >= this.MAX_VALUE_PROGRAMMING_RESULT_INTS ? `${this.MAX_VALUE_PROGRAMMING_RESULT_INTS}+` : result.codeIssueCount,
                points,
            });
        } else {
            return this.translateService.instant(`artemisApp.result.resultString.programming`, {
                relativeScore,
                buildAndTestMessage,
                points,
            });
        }
    }

    getResultsForExerciseWithPointsPerGradingCriterion(exerciseId: number, req?: any): Observable<ResultsWithPointsArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ResultWithPointsPerGradingCriterion[]>(`${this.exerciseResourceUrl}/${exerciseId}/results-with-points-per-criterion`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: ResultsWithPointsArrayResponseType) => this.convertResultsWithPointsResponse(res)));
    }

    getFeedbackDetailsForResult(participationId: number, result: Result): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.participationResourceUrl}/${participationId}/results/${result.id!}/details`, { observe: 'response' }).pipe(
            map((res) => {
                const feedbacks = res.body ?? [];
                feedbacks.forEach((feedback) => (feedback.result = result));
                return res;
            }),
        );
    }

    public convertResultDatesFromClient(result: Result): Result {
        return Object.assign({}, result, {
            completionDate: convertDateFromClient(result.completionDate),
            submission: undefined,
        });
    }

    protected convertArrayResponse(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((result: Result) => this.convertResultDatesFromServer(result));
        }
        return res;
    }

    protected convertResultsWithPointsResponse(res: ResultsWithPointsArrayResponseType): ResultsWithPointsArrayResponseType {
        if (res.body) {
            res.body.forEach((resultWithPoints: ResultWithPointsPerGradingCriterion) => {
                this.convertResultDatesFromServer(resultWithPoints.result);
                const pointsMap = new Map<number, number>();
                if (resultWithPoints.pointsPerCriterion) {
                    resultWithPoints.pointsPerCriterion.forEach((value, key) => {
                        pointsMap.set(Number(key), value);
                    });
                }
                resultWithPoints.pointsPerCriterion = pointsMap;
            });
        }
        return res;
    }

    private convertResultDatesFromServer(result: Result) {
        result.completionDate = convertDateFromServer(result.completionDate);
        ParticipationService.convertParticipationDatesFromServer(result.participation as StudentParticipation);
        SubmissionService.convertSubmissionDateFromServer(result.submission);
    }

    public convertResultResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            this.convertResultDatesFromServer(res.body);
        }
        return res;
    }

    /**
     * Fetches all results together with the total points and points per grading criterion for each of the given exercise.
     * @param exercise of which the results with points should be fetched.
     */
    getResultsWithPointsPerGradingCriterion(exercise: Exercise): Observable<ResultsWithPointsArrayResponseType> {
        return this.getResultsForExerciseWithPointsPerGradingCriterion(exercise.id!, {
            withSubmissions: exercise.type === ExerciseType.MODELING,
        }).pipe(
            tap((res: HttpResponse<ResultWithPointsPerGradingCriterion[]>) => {
                return res.body!.map((resultWithScores) => {
                    const result = resultWithScores.result;
                    resultWithScores.result = ResultService.processReceivedResult(exercise, result);
                    return resultWithScores;
                });
            }),
        );
    }

    public static processReceivedResult(exercise: Exercise, result: Result): Result {
        if (result.participation) {
            result.participation.results = [result];
            (result.participation as StudentParticipation).exercise = exercise;
            // Nest submission into participation so that it is available for the result component
            if (result.submission) {
                result.participation.submissions = [result.submission];
            }
        }
        result.durationInMinutes = ResultService.durationInMinutes(result.completionDate!, result.participation?.initializationDate ?? exercise.releaseDate!);
        return result;
    }

    /**
     * Utility function
     */
    private static durationInMinutes(completionDate: dayjs.Dayjs, initializationDate: dayjs.Dayjs) {
        return dayjs(completionDate).diff(initializationDate, 'minutes');
    }

    /**
     * Utility function used to trigger the download of a CSV file
     */
    public triggerDownloadCSV(rows: string[], csvFileName: string) {
        const csvContent = 'data:text/csv;charset=utf-8,' + rows.join('\n');
        this.csvDownloadService.downloadCSV(csvContent, csvFileName);
    }

    public static evaluateBadge(participation: Participation, result: Result): Badge {
        if (participation.type === ParticipationType.STUDENT || participation.type === ParticipationType.PROGRAMMING) {
            if (isPracticeMode(participation)) {
                return { class: 'bg-secondary', text: 'artemisApp.result.practice', tooltip: 'artemisApp.result.practiceTooltip' };
            }
        }
        return result.rated
            ? { class: 'bg-success', text: 'artemisApp.result.graded', tooltip: 'artemisApp.result.gradedTooltip' }
            : { class: 'bg-info', text: 'artemisApp.result.notGraded', tooltip: 'artemisApp.result.notGradedTooltip' };
    }
}
