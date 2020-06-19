import { Injectable } from '@angular/core';
import { SERVER_API_URL } from 'app/app.constants';
import { Observable, of } from 'rxjs';
import { Submission } from 'app/entities/submission.model';

@Injectable({ providedIn: 'root' })
export class ExamParticipationService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor() {}

    getLatestSubmissionForParticipation(id: number): Observable<any> {
        // TODO add service call
        return of(false);
    }

    updateSubmission(submission: Submission, id: number) {
        // TODO add service call
    }
}
