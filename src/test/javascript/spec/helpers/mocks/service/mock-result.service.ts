import { EMPTY, Observable, of } from 'rxjs';
import { IResultService, ResultsWithPointsArrayResponseType } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';

export class MockResultService implements IResultService {
    create = (result: Result) => of();
    delete = (participationId: number, resultId: number) => EMPTY;
    find = (resultId: number) => EMPTY;
    getFeedbackDetailsForResult = (participationId: number, result: Result) => EMPTY;
    getResultsForExerciseWithPointsPerGradingCriterion = (exerciseId: number, req: any) => EMPTY;
    getResultsWithPointsPerGradingCriterion = (exercise: Exercise): Observable<ResultsWithPointsArrayResponseType> => EMPTY;
    update = (result: Result) => of();
    getResults = (exercise: Exercise) => of();
    triggerDownloadCSV = (rows: string[], csvFileName: string) => EMPTY;
}
