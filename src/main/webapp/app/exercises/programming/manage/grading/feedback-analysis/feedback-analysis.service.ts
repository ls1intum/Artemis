import { Injectable } from '@angular/core';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
}

export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    taskNumber: number;
}

@Injectable()
export class FeedbackAnalysisService extends BaseApiHttpService {
    constructor() {
        super();
    }

    search(pageable: SearchTermPageableSearch, options: { exerciseId: number }): Promise<FeedbackAnalysisResponse> {
        return this.post<FeedbackAnalysisResponse>(`exercises/${options.exerciseId}/feedback-details-paged`, pageable);
    }
}
