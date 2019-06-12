import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs/Observable';

import { EntityArrayResponseType, EntityResponseType, IResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';

export class MockResultService implements IResultService {
    create = (result: Result) => of();
    delete = (id: number) => of();
    find = (id: number) => of();
    findBySubmissionId = (submissionId: number) => of();
    findResultsForParticipation = (courseId: number, exerciseId: number, participationId: number, req?: any) => of();
    getFeedbackDetailsForResult = (resultId: number) => of();
    getResultsForExercise = (courseId: number, exerciseId: number, req?: any) => of();
    update = (result: Result) => of();
    getLatestResultWithFeedbacks(particpationId: number): Observable<HttpResponse<Result>> {
        return of();
    }
}
