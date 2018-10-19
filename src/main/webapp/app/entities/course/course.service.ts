import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { DATE_FORMAT } from 'app/shared/constants/input.constants';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { createRequestOption } from 'app/shared';
import { ICourse } from 'app/shared/model/course.model';

type EntityResponseType = HttpResponse<ICourse>;
type EntityArrayResponseType = HttpResponse<ICourse[]>;

@Injectable({ providedIn: 'root' })
export class CourseService {
    public resourceUrl = SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) {}

    create(course: ICourse): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .post<ICourse>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    update(course: ICourse): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(course);
        return this.http
            .put<ICourse>(this.resourceUrl, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http
            .get<ICourse>(`${this.resourceUrl}/${id}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    query(req?: any): Observable<EntityArrayResponseType> {
        const options = createRequestOption(req);
        return this.http
            .get<ICourse[]>(this.resourceUrl, { params: options, observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response' });
    }

    private convertDateFromClient(course: ICourse): ICourse {
        const copy: ICourse = Object.assign({}, course, {
            startDate: course.startDate != null && course.startDate.isValid() ? course.startDate.toJSON() : null,
            endDate: course.endDate != null && course.endDate.isValid() ? course.endDate.toJSON() : null
        });
        return copy;
    }

    private convertDateFromServer(res: EntityResponseType): EntityResponseType {
        res.body.startDate = res.body.startDate != null ? moment(res.body.startDate) : null;
        res.body.endDate = res.body.endDate != null ? moment(res.body.endDate) : null;
        return res;
    }

    private convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        res.body.forEach((course: ICourse) => {
            course.startDate = course.startDate != null ? moment(course.startDate) : null;
            course.endDate = course.endDate != null ? moment(course.endDate) : null;
        });
        return res;
    }
}
