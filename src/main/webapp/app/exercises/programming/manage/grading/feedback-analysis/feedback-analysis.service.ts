import { Injectable } from '@angular/core';
import { PageableResult, PageableSearch, SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { HttpHeaders, HttpParams } from '@angular/common/http';
import { FilterData } from 'app/exercises/programming/manage/grading/feedback-analysis/Modal/feedback-filter-modal.component';
import { Course } from 'app/entities/course.model';
import { ChannelDTO } from 'app/entities/metis/conversation/channel.model';

export interface FeedbackAnalysisResponse {
    feedbackDetails: SearchResult<FeedbackDetail>;
    totalItems: number;
    taskNames: string[];
    testCaseNames: string[];
    errorCategories: string[];
}
export interface FeedbackDetail {
    concatenatedFeedbackIds: number[];
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
    repositoryURI: string;
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

    async getParticipationForFeedbackIds(exerciseId: number, feedbackIds: number[], pageable: PageableSearch): Promise<PageableResult<FeedbackAffectedStudentDTO>> {
        const feedbackIdsHeader = feedbackIds.join(',');

        const params = new HttpParams()
            .set('page', pageable.page.toString())
            .set('pageSize', pageable.pageSize.toString())
            .set('sortedColumn', pageable.sortedColumn)
            .set('sortingOrder', pageable.sortingOrder);

        const headers = new HttpHeaders().set('feedbackIds', feedbackIdsHeader);

        return this.get<PageableResult<FeedbackAffectedStudentDTO>>(`exercises/${exerciseId}/feedback-details-participation`, { params, headers });
    }

    getCourse(courseId: number): Promise<Course> {
        return this.get<Course>(`courses/${courseId}`);
    }

    createChannel(courseId: number, exerciseId: number, channelDTO: ChannelDTO, feedbackDetailText: string): Promise<ChannelDTO> {
        const headers = new HttpHeaders().set('feedback-detail-text', feedbackDetailText);

        return this.post<ChannelDTO>(`courses/${courseId}/${exerciseId}/feedback-channel`, channelDTO, { headers });
    }

    getAffectedStudentCount(exerciseId: number, feedbackDetailText: string): Promise<number> {
        const params = new HttpParams().set('detailText', feedbackDetailText);
        return this.get<number>(`exercises/${exerciseId}/feedback-detail/affected-students`, { params });
    }
}
