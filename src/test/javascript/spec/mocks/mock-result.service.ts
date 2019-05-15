import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { EntityArrayResponseType, EntityResponseType, IResultService, Result } from 'app/entities/result';
import { Feedback } from 'app/entities/feedback';

export class MockResultService implements IResultService {
    create: (result: Result) => Observable<EntityResponseType>;
    delete: (id: number) => Observable<HttpResponse<void>>;
    find: (id: number) => Observable<EntityResponseType>;
    findBySubmissionId: (submissionId: number) => Observable<EntityResponseType>;
    findResultsForParticipation: (courseId: number, exerciseId: number, participationId: number, req?: any) => Observable<EntityArrayResponseType>;
    getFeedbackDetailsForResult: (resultId: number) => Observable<HttpResponse<Feedback[]>>;
    getResultsForExercise: (courseId: number, exerciseId: number, req?: any) => Observable<EntityArrayResponseType>;
    update: (result: Result) => Observable<EntityResponseType>;
}
