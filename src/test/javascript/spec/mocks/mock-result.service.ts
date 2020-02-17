import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';
import { IResultService } from 'app/entities/result/result.service';
import { Result } from 'app/entities/result/result.model';

export class MockResultService implements IResultService {
    create = (result: Result) => of();
    delete = (id: number) => Observable.empty();
    find = (id: number) => Observable.empty();
    findBySubmissionId = (submissionId: number) => Observable.empty();
    getFeedbackDetailsForResult = (resultId: number) => Observable.empty();
    getResultsForExercise = (courseId: number, exerciseId: number, req?: any) => Observable.empty();
    update = (result: Result) => of();
    getLatestResultWithFeedbacks(particpationId: number): Observable<HttpResponse<Result>> {
        return of();
    }
}
