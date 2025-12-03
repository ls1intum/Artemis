import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import dayjs from 'dayjs/esm';

import { BaseCourseRequest, CourseRequest } from 'app/core/shared/entities/course-request.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';

@Injectable({ providedIn: 'root' })
export class CourseRequestService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/core/course-requests';
    private readonly adminResourceUrl = 'api/core/admin/course-requests';

    create(courseRequest: BaseCourseRequest): Observable<CourseRequest> {
        const copy = this.convertDateFromClient(courseRequest);
        return this.http.post<BaseCourseRequest>(this.resourceUrl, copy).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    findAllForAdmin(): Observable<CourseRequest[]> {
        return this.http.get<BaseCourseRequest[]>(this.adminResourceUrl).pipe(map((res) => this.convertResponseArrayFromServer(res)));
    }

    acceptRequest(id: number): Observable<CourseRequest> {
        return this.http.post<BaseCourseRequest>(`${this.adminResourceUrl}/${id}/accept`, {}).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    rejectRequest(id: number, reason: string): Observable<CourseRequest> {
        return this.http.post<BaseCourseRequest>(`${this.adminResourceUrl}/${id}/reject`, { reason }).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    private convertDateFromClient(courseRequest: BaseCourseRequest): BaseCourseRequest {
        return {
            ...courseRequest,
            startDate: convertDateFromClient(courseRequest.startDate) ?? undefined,
            endDate: convertDateFromClient(courseRequest.endDate) ?? undefined,
        };
    }

    private convertResponseFromServer(BaseCourseRequest: BaseCourseRequest): CourseRequest {
        return {
            ...BaseCourseRequest,
            startDate: this.convertDate(BaseCourseRequest.startDate),
            endDate: this.convertDate(BaseCourseRequest.endDate),
            createdDate: this.convertDate(BaseCourseRequest.createdDate),
            processedDate: this.convertDate(BaseCourseRequest.processedDate),
        };
    }

    private convertResponseArrayFromServer(BaseCourseRequests: BaseCourseRequest[]): CourseRequest[] {
        return BaseCourseRequests.map((request) => this.convertResponseFromServer(request));
    }

    private convertDate(value?: string): dayjs.Dayjs | undefined {
        return value ? dayjs(value) : undefined;
    }
}
