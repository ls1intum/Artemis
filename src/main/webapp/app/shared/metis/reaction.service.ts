import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { Reaction } from 'app/entities/metis/reaction.model';
import * as moment from 'moment';

type EntityResponseType = HttpResponse<Reaction>;

@Injectable({ providedIn: 'root' })
export class ReactionService {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * creates a reaction
     * @param {number} courseId
     * @param {Reaction} reaction
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, reaction: Reaction): Observable<EntityResponseType> {
        return this.http.post<Reaction>(`${this.resourceUrl}${courseId}/postings/reactions`, reaction, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * deletes a reaction
     * @param {number} courseId
     * @param {Reaction} reaction
     * @return {Observable<HttpResponse<void>>}
     */
    delete(courseId: number, reaction: Reaction): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}${courseId}/postings/reactions/${reaction.id}`, { observe: 'response' });
    }

    /**
     * takes a posting and converts the date from the server
     * @param   {HttpResponse<Reaction>} res
     * @return  {HttpResponse<Reaction>}
     */
    private convertDateFromServer(res: HttpResponse<Reaction>): HttpResponse<Reaction> {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? moment(res.body.creationDate) : undefined;
        }
        return res;
    }
}
