import { EMPTY, Observable } from 'rxjs';
import { IResultService, ResultsWithPointsArrayResponseType } from 'app/exercise/result/result.service';
import { Result } from 'app/entities/result.model';
import { Exercise } from 'app/entities/exercise.model';

export class MockResultService implements IResultService {
    find = (resultId: number) => EMPTY;
    getFeedbackDetailsForResult = (participationId: number, result: Result) => EMPTY;
    getResultsForExerciseWithPointsPerGradingCriterion = (exerciseId: number, req: any) => EMPTY;
    getResultsWithPointsPerGradingCriterion = (exercise: Exercise): Observable<ResultsWithPointsArrayResponseType> => EMPTY;
    triggerDownloadCSV = (rows: string[], csvFileName: string) => EMPTY;
}
