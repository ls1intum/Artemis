import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { Course } from './course.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<Course>;

@Injectable()
export class CourseService {

    private resourceUrl =  SERVER_API_URL + 'api/courses';

    constructor(private http: HttpClient) { }

    create(course: Course): Observable<EntityResponseType> {
        const copy = this.convert(course);
        return this.http.post<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(course: Course): Observable<EntityResponseType> {
        const copy = this.convert(course);
        return this.http.put<Course>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Course>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Course[]>> {
        const options = createRequestOption(req);
        return this.http.get<Course[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Course[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Course = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Course[]>): HttpResponse<Course[]> {
        const jsonResponse: Course[] = res.body;
        const body: Course[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Course.
     */
    private convertItemFromServer(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        return copy;
    }

    /**
     * Convert a Course to a JSON which can be sent to the server.
     */
    private convert(course: Course): Course {
        const copy: Course = Object.assign({}, course);
        return copy;
    }
}
