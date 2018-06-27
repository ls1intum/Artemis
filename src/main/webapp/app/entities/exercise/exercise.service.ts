import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { JhiDateUtils } from 'ng-jhipster';

import { Exercise } from './exercise.model';
import { createRequestOption } from '../../shared';
import { LtiConfiguration } from 'app/entities/lti-configuration';

export type EntityResponseType = HttpResponse<Exercise>;

@Injectable()
export class ExerciseService {

    private resourceUrl =  SERVER_API_URL + 'api/exercises';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convert(exercise);
        return this.http.post<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(exercise: Exercise): Observable<EntityResponseType> {
        const copy = this.convert(exercise);
        return this.http.put<Exercise>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Exercise>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    query(req?: any): Observable<HttpResponse<Exercise[]>> {
        const options = createRequestOption(req);
        return this.http.get<Exercise[]>(this.resourceUrl, { params: options, observe: 'response' })
            .map((res: HttpResponse<Exercise[]>) => this.convertArrayResponse(res));
    }

    delete(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}`, { observe: 'response'});
    }

    archive(id: number): Observable<HttpResponse<Blob>> {
        return this.http.get(`${this.resourceUrl}/${id}/archive`, { observe: 'response', responseType: 'blob'});
    }

    cleanup(id: number, deleteRepositories: boolean): Observable<HttpResponse<Blob>> {
        const params = new HttpParams().set('deleteRepositories', deleteRepositories.toString());
        return this.http.delete(`${this.resourceUrl}/${id}/cleanup`, { params, observe: 'response', responseType: 'blob'});
    }

    reset(id: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}/${id}/participations`, { observe: 'response'});
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Exercise = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertArrayResponse(res: HttpResponse<Exercise[]>): HttpResponse<Exercise[]> {
        const jsonResponse: Exercise[] = res.body;
        const body: Exercise[] = [];
        for (let i = 0; i < jsonResponse.length; i++) {
            body.push(this.convertItemFromServer(jsonResponse[i]));
        }
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Exercise.
     */
    private convertItemFromServer(exercise: Exercise): Exercise {
        const copy: Exercise = Object.assign({}, exercise);
        copy.releaseDate = this.dateUtils
            .convertDateTimeFromServer(exercise.releaseDate);
        copy.dueDate = this.dateUtils
            .convertDateTimeFromServer(exercise.dueDate);
        return copy;
    }

    /**
     * Convert a Exercise to a JSON which can be sent to the server.
     */
    private convert(exercise: Exercise): Exercise {
        const copy: Exercise = Object.assign({}, exercise);

        copy.releaseDate = this.dateUtils.toDate(exercise.releaseDate);

        copy.dueDate = this.dateUtils.toDate(exercise.dueDate);
        return copy;
    }
}

@Injectable()
export class ExerciseLtiConfigurationService {

    private resourceUrl =  SERVER_API_URL + 'api/lti/configuration';

    constructor(private http: HttpClient) { }

    find(id: number): Observable<HttpResponse<LtiConfiguration>> {
        return this.http.get<LtiConfiguration>(`${this.resourceUrl}/${id}`, { observe: 'response'}).
        map((res: HttpResponse<LtiConfiguration>) => this.convertResponse(res));
    }

    private convertResponse(res: HttpResponse<LtiConfiguration>): HttpResponse<LtiConfiguration> {
        const body: LtiConfiguration = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    private convertItemFromServer(ltiConfiguration: LtiConfiguration): LtiConfiguration {
        return Object.assign({}, ltiConfiguration);
    }
}
