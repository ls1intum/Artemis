import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    taskNumber: number;
}

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    distinctResultCount: number;
}

@Injectable({ providedIn: 'root' })
export class FeedbackAnalysisService extends PagingService<FeedbackDetail> {
    private resourceUrl = 'api';

    constructor(private http: HttpClient) {
        super();
    }

    override search(pageable: SearchTermPageableSearch, options: { exerciseId: number }): Observable<any> {
        return this.http
            .post<FeedbackAnalysisResponse>(`${this.resourceUrl}/exercises/${options.exerciseId}/feedback-details-paged`, pageable, { observe: 'response' })
            .pipe(map((resp: HttpResponse<FeedbackAnalysisResponse>) => resp.body!));
    }
}
