import { Injectable } from '@angular/core';
import { PageableResult, PageableSearch, SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { HttpParams } from '@angular/common/http';
import { FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
    taskNames: string[];
    testCaseNames: string[];
    errorCategories: string[];
    levenshteinMaxCount: number;
}
export interface FeedbackDetail {
    count: number;
    relativeCount: number;
    detailTexts: string[];
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
    repositoryURI: string;
}
export interface FeedbackChannelRequestDTO {
    channel: ChannelDTO;
    feedbackDetailTexts: string[];
    testCaseName: string;
}
@Injectable()
export class FeedbackAnalysisService extends BaseApiHttpService {
    search(pageable: SearchTermPageableSearch, levenshtein: boolean, options: { exerciseId: number; filters: FilterData }): Promise<FeedbackAnalysisResponse> {
        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('searchTerm', pageable.searchTerm || '')
            .set('sortingOrder', pageable.sortingOrder)
            .set('sortedColumn', pageable.sortedColumn)
            .set('filterTasks', options.filters.tasks.join(','))
            .set('filterTestCases', options.filters.testCases.join(','))
            .set('filterOccurrence', options.filters.occurrence.join(','))
            .set('filterErrorCategories', options.filters.errorCategories.join(','))
            .set('levenshtein', levenshtein.toString());

        return this.get<FeedbackAnalysisResponse>(`exercises/${options.exerciseId}/feedback-details`, { params });
    }

    getMaxCount(exerciseId: number): Promise<number> {
        return this.get<number>(`exercises/${exerciseId}/feedback-details-max-count`);
    }

    async getParticipationForFeedbackDetailText(
        exerciseId: number,
        detailText: string[],
        testCaseName: string,
        pageable: PageableSearch,
    ): Promise<PageableResult<FeedbackAffectedStudentDTO>> {
        let params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('sortedColumn', pageable.sortedColumn)
            .set('sortingOrder', pageable.sortingOrder)
            .set('testCaseName', testCaseName);

        const topDetailTexts = detailText.slice(0, 5);

        topDetailTexts.forEach((text, index) => {
            params = params.set(`detailText${index + 1}`, text);
        });

        return this.get<PageableResult<FeedbackAffectedStudentDTO>>(`exercises/${exerciseId}/feedback-details-participation`, { params });
    }

    createChannel(courseId: number, exerciseId: number, feedbackChannelRequest: FeedbackChannelRequestDTO): Promise<ChannelDTO> {
        return this.post<ChannelDTO>(`courses/${courseId}/${exerciseId}/feedback-channel`, feedbackChannelRequest);
    }
}
