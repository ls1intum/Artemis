import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
    BaseCourseRequest,
    CourseRequest,
    CourseRequestAccept,
    CourseRequestStatus,
    CourseRequestsAdminOverview,
    InstructorCourse,
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

interface CourseRequestDTO {
    id?: number;
    title: string;
    semester?: string;
    startDate?: string;
    endDate?: string;
    testCourse: boolean;
    reason: string;
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
}

interface InstructorCourseDTO {
    title: string;
    shortName: string;
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

    acceptRequest(courseRequestId: number, acceptData: CourseRequestAccept): Observable<CourseRequest> {
        const dto: CourseRequestAcceptDTO = {
            title: acceptData.title,
            shortName: acceptData.shortName,
            semester: acceptData.semester,
            startDate: convertDateFromClient(acceptData.startDate),
            endDate: convertDateFromClient(acceptData.endDate),
        };
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/accept`, dto).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    rejectRequest(courseRequestId: number, reason: string): Observable<CourseRequest> {
        return this.http.post<CourseRequestDTO>(`${this.adminResourceUrl}/${courseRequestId}/reject`, { reason }).pipe(map((res) => this.convertDTOToResponse(res)));
    }

    getInstructorCourses(userId: number): Observable<InstructorCourse[]> {
        return this.http.get<InstructorCourseDTO[]>(`${this.adminResourceUrl}/instructor-courses/${userId}`).pipe(
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
        return {
            title: courseRequest.title,
            testCourse: courseRequest.testCourse,
            reason: courseRequest.reason,
            semester: courseRequest.semester,
            startDate: convertDateFromClient(courseRequest.startDate),
            endDate: convertDateFromClient(courseRequest.endDate),
        };
    }

    private convertDTOToResponse(dto: CourseRequestDTO): CourseRequest {
        return {
            title: dto.title,
            testCourse: dto.testCourse,
            reason: dto.reason,
            id: dto.id,
            semester: dto.semester,
            startDate: convertDateStringFromServer(dto.startDate),
            endDate: convertDateStringFromServer(dto.endDate),
            status: dto.status,
            createdDate: convertDateStringFromServer(dto.createdDate),
            processedDate: convertDateStringFromServer(dto.processedDate),
            decisionReason: dto.decisionReason,
            requester: dto.requester,
            createdCourseId: dto.createdCourseId,
            instructorCourseCount: dto.instructorCourseCount,
        };
    }
}
