import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { Reaction, ReactionDTO } from 'app/communication/shared/entities/reaction.model';

type EntityResponseType = HttpResponse<Reaction>;

@Injectable({ providedIn: 'root' })
export class ReactionService {
    private http = inject(HttpClient);

    public resourceUrl = 'api/communication/courses/';

    /**
     * creates a reaction
     * @param courseId
     * @param reaction
     * @return the create reaction
     */
    create(courseId: number, reaction: Reaction): Observable<EntityResponseType> {
        return this.http
            .post<Reaction>(`${this.resourceUrl}${courseId}/postings/reactions`, ReactionDTO.fromReaction(reaction), { observe: 'response' })
            .pipe(map(this.convertPostingResponseDateFromServer));
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
    private convertPostingResponseDateFromServer(res: HttpResponse<Reaction>): HttpResponse<Reaction> {
        if (res.body) {
            res.body.creationDate = convertDateFromServer(res.body.creationDate);
        }
        return res;
    }
}
