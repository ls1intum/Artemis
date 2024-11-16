import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingService } from 'app/shared/metis/posting.service';
import { catchError, map } from 'rxjs/operators';
import { throwError } from 'rxjs';

type EntityResponseType = HttpResponse<AnswerPost>;

@Injectable({ providedIn: 'root' })
export class AnswerPostService extends PostingService<AnswerPost> {
    public resourceUrl = 'api/courses/';

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
        const copy = this.convertPostingDateFromClient(answerPost);
        return this.http
            .post<AnswerPost>(`${this.resourceUrl}${courseId}${AnswerPostService.getResourceEndpoint(answerPost)}`, copy, { observe: 'response' })
            .pipe(map(this.convertPostingResponseDateFromServer));
    }

    /**
     * updates an answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertPostingDateFromClient(answerPost);
        return this.http
            .put<AnswerPost>(`${this.resourceUrl}${courseId}${AnswerPostService.getResourceEndpoint(answerPost)}/${answerPost.id}`, copy, { observe: 'response' })
            .pipe(map(this.convertPostingResponseDateFromServer));
    }

    /**
     * deletes an answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<HttpResponse<void>>}
     */
    delete(courseId: number, answerPost: AnswerPost): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}${courseId}${AnswerPostService.getResourceEndpoint(answerPost)}/${answerPost.id}`, { observe: 'response' });
    }

    /**
     * gets an answer post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<HttpResponse<void>>}
     */
    getAnswerPostById(courseId: number, answerPostId: number): Observable<EntityResponseType> {
        return this.http.get<AnswerPost>(`${this.resourceUrl}${courseId}/answer-messages/${answerPostId}`, { observe: 'response' }).pipe(
            map((response) => {
                return response;
            }),
            catchError((error) => {
                return throwError(() => new Error(`${error.message || error.statusText}`));
            }),
        );
    }

    private static getResourceEndpoint(param: AnswerPost): string {
        if (param.post?.conversation) {
            return '/answer-messages';
        } else {
            return '/answer-posts';
        }
    }
}
