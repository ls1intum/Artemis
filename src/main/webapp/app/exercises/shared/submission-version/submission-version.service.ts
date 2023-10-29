import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Submission } from 'app/entities/submission.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/utils/date.utils';
import { SubmissionVersion } from 'app/entities/submission-version.model';

export type EntityResponseType = HttpResponse<Submission>;
export type EntityArrayResponseType = HttpResponse<Submission[]>;

@Injectable({ providedIn: 'root' })
export class SubmissionVersionService {
    public resourceUrl = 'api/submissions';

    constructor(private http: HttpClient) {}

    /**
     * Find all submission versions for a given submission id.
     * This works because every participation has exactly one submission for all exercise types that support submission versions.
     * @param submissionId the id of the submission
     */
    findAllSubmissionVersionsOfSubmission(submissionId: number): Observable<SubmissionVersion[]> {
        return this.http.get<SubmissionVersion[]>(`${this.resourceUrl}/${submissionId}/versions`).pipe(map((res) => this.convertCreatedDatesFromServer(res)));
    }
    private convertCreatedDatesFromServer(res: SubmissionVersion[]): SubmissionVersion[] {
        return res.map((version) => {
            return { ...version, createdDate: convertDateFromServer(version.createdDate)! };
        });
    }
}
