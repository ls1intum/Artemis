import { Injectable } from '@angular/core';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import { HttpParams } from '@angular/common/http';
import { FilterData } from 'app/programming/manage/grading/feedback-analysis/modal/feedback-filter/feedback-filter-modal.component';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
    taskNames: string[];
    testCaseNames: string[];
    errorCategories: string[];
    highestOccurrenceOfGroupedFeedback: number;
}
export interface FeedbackDetail {
    feedbackIds: number[];
    count: number;
    relativeCount: number;
    detailTexts: string[];
    testCaseName: string;
    taskName: string;
    errorCategory: string;
    hasLongFeedbackText: boolean;
}
export interface FeedbackAffectedStudentDTO {
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
    search(pageable: SearchTermPageableSearch, groupFeedback: boolean, options: { exerciseId: number; filters: FilterData }): Promise<FeedbackAnalysisResponse> {
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
            .set('groupFeedback', groupFeedback.toString());

        return this.get<FeedbackAnalysisResponse>(`assessment/exercises/${options.exerciseId}/feedback-details`, { params });
    }

    getMaxCount(exerciseId: number): Promise<number> {
        return this.get<number>(`assessment/exercises/${exerciseId}/feedback-details-max-count`);
    }

    async getParticipationForFeedbackDetailText(exerciseId: number, feedbackIds: number[]): Promise<FeedbackAffectedStudentDTO[]> {
        let params = new HttpParams();
        const topFeedbackIds = feedbackIds.slice(0, 5);

        topFeedbackIds.forEach((id, index) => {
            params = params.set(`feedbackId${index + 1}`, id.toString());
        });

        return this.get<FeedbackAffectedStudentDTO[]>(`assessment/exercises/${exerciseId}/feedback-details-participation`, { params });
    }

    createChannel(courseId: number, exerciseId: number, feedbackChannelRequest: FeedbackChannelRequestDTO): Promise<ChannelDTO> {
        return this.post<ChannelDTO>(`communication/courses/${courseId}/exercises/${exerciseId}/feedback-channel`, feedbackChannelRequest);
    }
}
