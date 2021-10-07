import { HttpResponse } from '@angular/common/http';
import { Observable, of, empty } from 'rxjs';
import { IResultService } from 'app/exercises/shared/result/result.service';
import { Result } from 'app/entities/result.model';

export class MockResultService implements IResultService {
    create = (result: Result) => of();
    delete = (participationId: number, resultId: number) => empty();
    find = (id: number) => empty();
    getFeedbackDetailsForResult = (participationId: number, resultId: number) => empty();
    getResultsForExercise = (courseId: number, exerciseId: number, req?: any) => empty();
    getResultsForExerciseWithPointsPerGradingCriterion = (exerciseId: number, req: any) => empty();
    update = (result: Result) => of();
    getLatestResultWithFeedbacks(particpationId: number): Observable<HttpResponse<Result>> {
        return of();
    }
}
