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
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { map, tap } from 'rxjs/operators';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { convertDateFromClient, convertDateFromServer } from 'app/utils/date.utils';

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

    constructor(private http: HttpClient) {}

    find(resultId: number): Observable<EntityResponseType> {
        return this.http
            .get<Result>(`${this.resultResourceUrl}/${resultId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertResultResponseDatesFromServer(res)));
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
}
