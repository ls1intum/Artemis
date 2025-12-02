import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import dayjs from 'dayjs/esm';

import { CourseRequest, NewCourseRequest } from 'app/core/shared/entities/course-request.model';
import { convertDateFromClient } from 'app/shared/util/date.utils';

type RestCourseRequest = Omit<CourseRequest, 'startDate' | 'endDate' | 'createdDate' | 'processedDate'> & {
    startDate?: string;
    endDate?: string;
    createdDate?: string;
    processedDate?: string;
};

@Injectable({ providedIn: 'root' })
export class CourseRequestService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/core/course-requests';
    private readonly adminResourceUrl = 'api/core/admin/course-requests';

    create(courseRequest: NewCourseRequest): Observable<CourseRequest> {
        const copy = this.convertDateFromClient(courseRequest);
        return this.http.post<RestCourseRequest>(this.resourceUrl, copy).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    findAllForAdmin(): Observable<CourseRequest[]> {
        return this.http.get<RestCourseRequest[]>(this.adminResourceUrl).pipe(map((res) => this.convertResponseArrayFromServer(res)));
    }

    acceptRequest(id: number): Observable<CourseRequest> {
        return this.http.post<RestCourseRequest>(`${this.adminResourceUrl}/${id}/accept`, {}).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    rejectRequest(id: number, reason: string): Observable<CourseRequest> {
        return this.http.post<RestCourseRequest>(`${this.adminResourceUrl}/${id}/reject`, { reason }).pipe(map((res) => this.convertResponseFromServer(res)));
    }

    private convertDateFromClient(courseRequest: NewCourseRequest): RestCourseRequest {
        return {
            ...courseRequest,
            startDate: convertDateFromClient(courseRequest.startDate) ?? undefined,
            endDate: convertDateFromClient(courseRequest.endDate) ?? undefined,
        };
    }

    private convertResponseFromServer(restCourseRequest: RestCourseRequest): CourseRequest {
        return {
            ...restCourseRequest,
            startDate: this.convertDate(restCourseRequest.startDate),
            endDate: this.convertDate(restCourseRequest.endDate),
            createdDate: this.convertDate(restCourseRequest.createdDate),
            processedDate: this.convertDate(restCourseRequest.processedDate),
        };
    }

    private convertResponseArrayFromServer(restCourseRequests: RestCourseRequest[]): CourseRequest[] {
        return restCourseRequests.map((request) => this.convertResponseFromServer(request));
    }

    private convertDate(value?: string): dayjs.Dayjs | undefined {
        return value ? dayjs(value) : undefined;
    }
}
