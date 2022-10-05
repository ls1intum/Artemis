import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Result } from 'app/entities/result.model';
import { ResultWithPointsPerGradingCriterion } from 'app/entities/result-with-points-per-grading-criterion.model';
import { createRequestOption } from 'app/shared/util/request.util';
import { Feedback } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType, getCourseFromExercise } from 'app/entities/exercise.model';
import { map, tap } from 'rxjs/operators';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';
import { TranslateService } from '@ngx-translate/core';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { isResultPreliminary } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { captureException } from '@sentry/browser';
import { Participation, ParticipationType } from 'app/entities/participation/participation.model';

export type EntityResponseType = HttpResponse<Result>;
export type EntityArrayResponseType = HttpResponse<Result[]>;
export type ResultsWithPointsArrayResponseType = HttpResponse<ResultWithPointsPerGradingCriterion[]>;

export interface IResultService {
    find: (resultId: number) => Observable<EntityResponseType>;
    getResultsForExercise: (courseId: number, exerciseId: number, req?: any) => Observable<EntityArrayResponseType>;
    getResultsForExerciseWithPointsPerGradingCriterion: (exerciseId: number, req?: any) => Observable<ResultsWithPointsArrayResponseType>;
    getLatestResultWithFeedbacks: (participationId: number) => Observable<HttpResponse<Result>>;
    getFeedbackDetailsForResult: (participationId: number, resultId: number) => Observable<HttpResponse<Feedback[]>>;
    delete: (participationId: number, resultId: number) => Observable<HttpResponse<void>>;
}

@Injectable({ providedIn: 'root' })
export class ResultService implements IResultService {
    private exerciseResourceUrl = SERVER_API_URL + 'api/exercises';
    private resultResourceUrl = SERVER_API_URL + 'api/results';
    private participationResourceUrl = SERVER_API_URL + 'api/participations';

    private readonly maxValueProgrammingResultInts = 255; // Size of tinyInt in SQL, that is used to store these values

    constructor(private http: HttpClient, private translateService: TranslateService) {}

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
            return this.getResultStringNonProgrammingExercise(relativeScore, points, short);
        } else {
            return this.getResultStringProgrammingExercise(result, exercise as ProgrammingExercise, relativeScore, points, short);
        }
    }

    /**
     * Generates the result string for a programming exercise. Contains the score and points
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param short flag that indicates if the resultString should use the short format
     */
    private getResultStringNonProgrammingExercise(relativeScore: number, points: number, short: boolean | undefined): string {
        if (short) {
            return this.translateService.instant(`artemisApp.result.resultStringShort`, {
                relativeScore,
            });
        } else {
            return this.translateService.instant(`artemisApp.result.resultStringNonProgramming`, {
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
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultStringBuildFailed');
        } else if (!result.testCaseCount) {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultStringBuildSuccessfulNoTests');
        } else {
            buildAndTestMessage = this.translateService.instant('artemisApp.result.resultStringBuildSuccessfulTests', {
                numberOfTestsPassed: result.passedTestCaseCount! >= this.maxValueProgrammingResultInts ? `${this.maxValueProgrammingResultInts}+` : result.passedTestCaseCount,
                numberOfTestsTotal: result.testCaseCount! >= this.maxValueProgrammingResultInts ? `${this.maxValueProgrammingResultInts}+` : result.testCaseCount,
            });
        }

        let resultString = this.getBaseResultStringProgrammingExercise(result, exercise, relativeScore, points, buildAndTestMessage, short);

        if (isResultPreliminary(result, exercise)) {
            resultString += ' (' + this.translateService.instant('artemisApp.result.preliminary') + ')';
        }

        return resultString;
    }

    /**
     * Generates the result string for a programming exercise
     * @param result the result containing all necessary information like the achieved points
     * @param exercise the exercise where the result belongs to
     * @param relativeScore the achieved score in percent
     * @param points the amount of achieved points
     * @param buildAndTestMessage the string containing information about the build. Either about the build failure or the passed tests
     * @param short flag that indicates if the resultString should use the short format
     * @private
     */
    private getBaseResultStringProgrammingExercise(
        result: Result,
        exercise: ProgrammingExercise,
        relativeScore: number,
        points: number,
        buildAndTestMessage: string,
        short: boolean | undefined,
    ): string {
        if (short) {
            if (!result.testCaseCount) {
                return this.translateService.instant('artemisApp.result.resultStringProgrammingShort', {
                    relativeScore,
                    buildAndTestMessage,
                });
            } else {
                return this.translateService.instant('artemisApp.result.resultStringShort', {
                    relativeScore,
                });
            }
        } else if (result.codeIssueCount && result.codeIssueCount > 0) {
            return this.translateService.instant('artemisApp.result.resultStringProgrammingCodeIssues', {
                relativeScore,
                buildAndTestMessage,
                numberOfIssues: result.codeIssueCount! >= this.maxValueProgrammingResultInts ? `${this.maxValueProgrammingResultInts}+` : result.codeIssueCount,
                points,
            });
        } else {
            return this.translateService.instant(`artemisApp.result.resultStringProgramming`, {
                relativeScore,
                buildAndTestMessage,
                points,
            });
        }
    }

    getResultsForExercise(exerciseId: number, req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<Result[]>(`${this.exerciseResourceUrl}/${exerciseId}/results`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.convertArrayResponse(res)));
    }

    getResultsForExerciseWithPointsPerGradingCriterion(exerciseId: number, req?: any): Observable<ResultsWithPointsArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ResultWithPointsPerGradingCriterion[]>(`${this.exerciseResourceUrl}/${exerciseId}/results-with-points-per-criterion`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: ResultsWithPointsArrayResponseType) => this.convertResultWithPointsResponse(res)));
    }

    getFeedbackDetailsForResult(participationId: number, resultId: number): Observable<HttpResponse<Feedback[]>> {
        return this.http.get<Feedback[]>(`${this.participationResourceUrl}/${participationId}/results/${resultId}/details`, { observe: 'response' });
    }

    getLatestResultWithFeedbacks(participationId: number): Observable<HttpResponse<Result>> {
        return this.http.get<Result>(`${this.participationResourceUrl}/${participationId}/latest-result`, { observe: 'response' });
    }

    delete(participationId: number, resultId: number): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' });
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

    protected convertResultWithPointsResponse(res: ResultsWithPointsArrayResponseType): ResultsWithPointsArrayResponseType {
        if (res.body) {
            res.body.forEach((resultWithPoints: ResultWithPointsPerGradingCriterion) => {
                this.convertResultDatesFromServer(resultWithPoints.result);
                const pointsMap = new Map<number, number>();
                Object.keys(resultWithPoints.pointsPerCriterion).forEach((key) => {
                    pointsMap.set(Number(key), resultWithPoints.pointsPerCriterion[key]);
                });
                resultWithPoints.pointsPerCriterion = pointsMap;
            });
        }
        return res;
    }

    private convertResultDatesFromServer(result: Result) {
        result.completionDate = convertDateFromServer(result.completionDate);
        result.participation = this.convertParticipationDatesFromServer(result.participation! as StudentParticipation);
    }

    public convertResultResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.completionDate = convertDateFromServer(res.body.completionDate);
            res.body.participation = this.convertParticipationDatesFromServer(res.body.participation! as StudentParticipation);
        }
        return res;
    }

    private convertParticipationDatesFromServer(participation: StudentParticipation): StudentParticipation {
        if (participation) {
            ParticipationService.convertParticipationDatesFromServer(participation);
            if (participation.exercise) {
                participation.exercise = ExerciseService.convertExerciseDatesFromServer(participation.exercise);
            }
        }
        return participation;
    }

    /**
     * Fetches all results for an exercise and returns them
     */
    getResults(exercise: Exercise) {
        return this.getResultsForExercise(exercise.id!, {
            withSubmissions: exercise.type === ExerciseType.MODELING,
        }).pipe(
            tap((res: HttpResponse<Result[]>) => {
                return res.body!.map((result) => ResultService.processReceivedResult(exercise, result));
            }),
        );
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

    private static processReceivedResult(exercise: Exercise, result: Result): Result {
        result.participation!.results = [result];
        (result.participation! as StudentParticipation).exercise = exercise;
        result.durationInMinutes = ResultService.durationInMinutes(
            result.completionDate!,
            result.participation!.initializationDate ? result.participation!.initializationDate : exercise.releaseDate!,
        );
        // Nest submission into participation so that it is available for the result component
        if (result.submission) {
            result.participation!.submissions = [result.submission];
        }
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
        const csvContent = rows.join('\n');
        const encodedUri = encodeURI(csvContent);
        const link = document.createElement('a');
        link.setAttribute('href', encodedUri);
        link.setAttribute('download', `${csvFileName}`);
        document.body.appendChild(link); // Required for FF
        link.click();
    }

    public static evaluateBadge(participation: Participation, result: Result): { badgeClass: string; text: string; tooltip: string } {
        if (participation.type === ParticipationType.STUDENT || participation.type === ParticipationType.PROGRAMMING) {
            const studentParticipation = participation as StudentParticipation;
            if (studentParticipation.testRun) {
                return { badgeClass: 'bg-secondary', text: 'artemisApp.result.practice', tooltip: 'artemisApp.result.practiceTooltip' };
            }
        }
        return result.rated
            ? { badgeClass: 'bg-success', text: 'artemisApp.result.graded', tooltip: 'artemisApp.result.gradedTooltip' }
            : { badgeClass: 'bg-info', text: 'artemisApp.result.notGraded', tooltip: 'artemisApp.result.notGradedTooltip' };
    }
}
