import { Injectable } from '@angular/core';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { HttpParams } from '@angular/common/http';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
    totalAmountOfTasks: number;
    testCaseNames: [];
    maxCount: number;
}
export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    taskNumber: string;
    errorCategory: string;
}
@Injectable()
export class FeedbackAnalysisService extends BaseApiHttpService {
    search(pageable: SearchTermPageableSearch, options: { exerciseId: number; filters: any }): Promise<FeedbackAnalysisResponse> {
        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('searchTerm', pageable.searchTerm || '')
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn)
            .set('filterTasks', options.filters.tasks.join(','))
            .set('filterTestCases', options.filters.testCases.join(','))
            .set('filterOccurrence', options.filters.occurrence.join(','));

        return this.get<FeedbackAnalysisResponse>(`exercises/${options.exerciseId}/feedback-details-paged`, { params });
    }
}
