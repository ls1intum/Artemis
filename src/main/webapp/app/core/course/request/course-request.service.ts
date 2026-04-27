import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
    BaseCourseRequest,
    CourseRequest,
    CourseRequestAcceptPayload,
    CourseRequestStatus,
    CourseRequestsAdminOverview,
    RequesterCourse,
} from 'app/core/shared/entities/course-request.model';
import { User } from 'app/core/user/user.model';
import { convertDateFromClient, convertDateStringFromServer } from 'app/shared/util/date.utils';

interface BaseCourseRequestDTO {
    title: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    testCourse: boolean;
    reason: string;
}

interface CourseRequestDTO extends BaseCourseRequestDTO {
    id?: number;
    shortName?: string;
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

interface CourseRequestAcceptDTO {
    title: string;
    shortName: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    testCourse: boolean;
}

interface RequesterCourseDTO {
    title?: string;
    shortName?: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
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
                    pendingRequests: (res.pendingRequests ?? []).map((dto) => this.convertDTOToResponse(dto)),
                    decidedRequests: (res.decidedRequests ?? []).map((dto) => this.convertDTOToResponse(dto)),
                    totalDecidedCount: res.totalDecidedCount ?? 0,
                })),
            );
    }

    acceptRequest(courseRequestId: number, payload: CourseRequestAcceptPayload): Observable<CourseRequest> {
        const dto: CourseRequestAcceptDTO = {
            title: payload.title,
            shortName: payload.shortName,
            semester: payload.semester,
            startDate: convertDateFromClient(payload.startDate),
            endDate: convertDateFromClient(payload.endDate),
            testCourse: payload.testCourse,
        };
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/accept`, dto).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    rejectRequest(courseRequestId: number, reason: string): Observable<CourseRequest> {
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/reject`, { reason }).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    getRequesterCourses(courseRequestId: number): Observable<RequesterCourse[]> {
        return this.http.get<RequesterCourseDTO[]>(`${this.adminResourceUrl}/${courseRequestId}/requester-courses`).pipe(
            map((dtos) =>
                dtos.map((dto) => ({
                    title: dto.title,
                    shortName: dto.shortName,
                    semester: dto.semester,
                    startDate: convertDateStringFromServer(dto.startDate),
                    endDate: convertDateStringFromServer(dto.endDate),
                })),
            ),
        );
    }

    private convertRequestToDTO(courseRequest: BaseCourseRequest): BaseCourseRequestDTO {
        const dto: BaseCourseRequestDTO = {
            title: courseRequest.title,
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
            testCourse: dto.testCourse,
            reason: dto.reason,
        };
        response.id = dto.id;
        response.shortName = dto.shortName;
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
