import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingsService } from 'app/shared/metis/postings.service';

type EntityResponseType = HttpResponse<AnswerPost>;

@Injectable({ providedIn: 'root' })
export class AnswerPostService extends PostingsService<AnswerPost> {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {
        super();
    }

    /**
     * creates an answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(answerPost);
        return this.http.post<AnswerPost>(`${this.resourceUrl}${courseId}/answer-posts`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * updates an answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(answerPost);
        return this.http.put<AnswerPost>(`${this.resourceUrl}${courseId}/answer-posts`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * deletes an answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        return this.http.delete<AnswerPost>(`${this.resourceUrl}${courseId}/answer-posts/${answerPost.id}`, { observe: 'response' });
    }
}
