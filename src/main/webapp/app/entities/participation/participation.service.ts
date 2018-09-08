import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';
import { SERVER_API_URL } from '../../app.constants';

import { JhiDateUtils } from 'ng-jhipster';

import { Participation } from './participation.model';
import { createRequestOption } from '../../shared';

export type EntityResponseType = HttpResponse<Participation>;

@Injectable()
export class ParticipationService {

    private resourceUrl =  SERVER_API_URL + 'api/participations';

    constructor(private http: HttpClient, private dateUtils: JhiDateUtils) { }

    create(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convert(participation);
        return this.http.post<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    update(participation: Participation): Observable<EntityResponseType> {
        const copy = this.convert(participation);
        return this.http.put<Participation>(this.resourceUrl, copy, { observe: 'response' })
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    find(id: number): Observable<EntityResponseType> {
        return this.http.get<Participation>(`${this.resourceUrl}/${id}`, { observe: 'response'})
            .map((res: EntityResponseType) => this.convertResponse(res));
    }

    /*
     * Finds one participation for the currently logged in user for the given exercise in the given course
     */
    findParticipation(courseId: number, exerciseId: number): Observable<Participation> {
        return this.http.get<Participation>(SERVER_API_URL + `api/courses/${courseId}/exercises/${exerciseId}/participation`).map((res: Participation) => {
            if (typeof res === 'undefined' || res === null) {
                return null;
            }
            return this.convertItemFromServer(res);
        });
    }

    findAllParticipationsByExercise(exerciseId: number): Observable<Participation[]> {
        return this.http.get<Participation[]>(SERVER_API_URL + `api/exercise/${exerciseId}/participations`);
    }

    delete(id: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${id}`, { params: options, observe: 'response' });
    }

    repositoryWebUrl(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/repositoryWebUrl`, { responseType: 'text' }).map(repositoryWebUrl => {
            return {url: repositoryWebUrl};
        });
    }

    buildPlanWebUrl(participationId: number) {
        return this.http.get(`${this.resourceUrl}/${participationId}/buildPlanWebUrl`, { responseType: 'text' }).map(buildPlanWebUrl => {
            return {url: buildPlanWebUrl};
        });
    }

    downloadArtifact(id: number) {
        return this.http.get(`${this.resourceUrl}/${id}/buildArtifact`, { responseType: 'blob' }).map(artifact => {
            return artifact;
        });
    }

    private convertResponse(res: EntityResponseType): EntityResponseType {
        const body: Participation = this.convertItemFromServer(res.body);
        return res.clone({body});
    }

    /**
     * Convert a returned JSON object to Participation.
     */
    private convertItemFromServer(participation: Participation): Participation {
        const copy: Participation = Object.assign({}, participation);
        copy.initializationDate = this.dateUtils.convertDateTimeFromServer(participation.initializationDate);
        return copy;
    }

    /**
     * Convert a Participation to a JSON which can be sent to the server.
     */
    private convert(participation: Participation): Participation {
        const copy: Participation = Object.assign({}, participation);
        copy.initializationDate = this.dateUtils.toDate(participation.initializationDate);
        return copy;
    }
}
