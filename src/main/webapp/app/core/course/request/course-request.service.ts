import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { BaseCourseRequest, CourseRequest, CourseRequestStatus, CourseRequestsAdminOverview } from 'app/core/shared/entities/course-request.model';
import { User } from 'app/core/user/user.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/shared/util/date.utils';

interface BaseCourseRequestDTO {
    title: string;
    shortName: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    testCourse: boolean;
    reason: string;
}

interface CourseRequestDTO extends BaseCourseRequestDTO {
    id?: number;
    status?: CourseRequestStatus;
    createdDate?: string;
    processedDate?: string;
    decisionReason?: string;
    requester?: User;
    createdCourseId?: number;
    instructorCourseCount?: number;
}

interface CourseRequestsAdminOverviewDTO {
    pendingRequests: CourseRequestDTO[];
    decidedRequests: CourseRequestDTO[];
    totalDecidedCount: number;
}

@Injectable({ providedIn: 'root' })
export class CourseRequestService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/core/course-requests';
    private readonly adminResourceUrl = 'api/core/admin/course-requests';

    create(courseRequest: BaseCourseRequest): Observable<CourseRequest> {
        const dto = this.convertRequestToDTO(courseRequest);
        return this.http.post<CourseRequestDTO>(this.resourceUrl, dto).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    findAdminOverview(decidedPage = 0, decidedPageSize = 20): Observable<CourseRequestsAdminOverview> {
        return this.http
            .get<CourseRequestsAdminOverviewDTO>(`${this.adminResourceUrl}/overview`, {
                params: { decidedPage: decidedPage.toString(), decidedPageSize: decidedPageSize.toString() },
            })
            .pipe(
                map((res) => ({
                    pendingRequests: res.pendingRequests.map((dto) => this.convertDTOToResponse(dto)),
                    decidedRequests: res.decidedRequests.map((dto) => this.convertDTOToResponse(dto)),
                    totalDecidedCount: res.totalDecidedCount,
                })),
            );
    }

    acceptRequest(courseRequestId: number): Observable<CourseRequest> {
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/accept`, {}).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    rejectRequest(courseRequestId: number, reason: string): Observable<CourseRequest> {
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/reject`, { reason }).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    private convertRequestToDTO(courseRequest: BaseCourseRequest): BaseCourseRequestDTO {
        const dto: BaseCourseRequestDTO = {
            title: courseRequest.title,
            shortName: courseRequest.shortName,
            testCourse: courseRequest.testCourse,
            reason: courseRequest.reason,
        };
        dto.semester = courseRequest.semester;
        dto.startDate = convertDateFromClient(courseRequest.startDate);
        dto.endDate = convertDateFromClient(courseRequest.endDate);
        return dto;
    }

    private convertDTOToResponse(dto: CourseRequestDTO): CourseRequest {
        const response: CourseRequest = {
            title: dto.title,
            shortName: dto.shortName,
            testCourse: dto.testCourse,
            reason: dto.reason,
        };
        response.id = dto.id;
        response.semester = dto.semester;
        response.startDate = convertDateStringFromServer(dto.startDate);
        response.endDate = convertDateStringFromServer(dto.endDate);
        response.status = dto.status;
        response.createdDate = convertDateStringFromServer(dto.createdDate);
        response.processedDate = convertDateStringFromServer(dto.processedDate);
        response.decisionReason = dto.decisionReason;
        response.requester = dto.requester;
        response.createdCourseId = dto.createdCourseId;
        response.instructorCourseCount = dto.instructorCourseCount;
        return response;
    }
}
