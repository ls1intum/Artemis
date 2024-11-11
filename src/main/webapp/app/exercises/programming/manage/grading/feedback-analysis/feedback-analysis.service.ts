import { Injectable } from '@angular/core';
import { PageableResult, PageableSearch, SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { HttpParams } from '@angular/common/http';
import { FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
    taskNames: string[];
    testCaseNames: string[];
    errorCategories: string[];
}
export interface FeedbackDetail {
    concatenatedFeedbackIds: string;
    count: number;
    relativeCount: number;
    detailText: string;
    testCaseName: string;
    taskName: string;
    errorCategory: string;
}
export interface FeedbackAffectedStudentDTO {
    courseId: number;
    participationId: number;
    firstName: string;
    lastName: string;
    login: string;
    repositoryName: string;
}
@Injectable()
export class FeedbackAnalysisService extends BaseApiHttpService {
    search(pageable: SearchTermPageableSearch, options: { exerciseId: number; filters: FilterData }): Promise<FeedbackAnalysisResponse> {
        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('searchTerm', pageable.searchTerm || '')
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn)
            .set('filterTasks', options.filters.tasks.join(','))
            .set('filterTestCases', options.filters.testCases.join(','))
            .set('filterOccurrence', options.filters.occurrence.join(','))
            .set('filterErrorCategories', options.filters.errorCategories.join(','));

        return this.get<FeedbackAnalysisResponse>(`exercises/${options.exerciseId}/feedback-details`, { params });
    }

    getMaxCount(exerciseId: number): Promise<number> {
        return this.get<number>(`exercises/${exerciseId}/feedback-details-max-count`);
    }

    async getParticipationForFeedbackIds(exerciseId: number, feedbackIds: string, pageable: PageableSearch): Promise<PageableResult<FeedbackAffectedStudentDTO>> {
        const params = new HttpParams()
            .set('feedbackIds', feedbackIds)
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('sortedColumn', pageable.sortedColumn)
            .set('sortingOrder', pageable.sortingOrder);

        return this.get<PageableResult<FeedbackAffectedStudentDTO>>(`exercises/${exerciseId}/feedback-details-participation`, { params });
    }
}
