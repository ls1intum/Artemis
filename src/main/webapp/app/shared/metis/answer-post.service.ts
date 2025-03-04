import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingService } from 'app/shared/metis/posting.service';
import { map } from 'rxjs/operators';

type EntityResponseType = HttpResponse<AnswerPost>;

@Injectable({ providedIn: 'root' })
export class AnswerPostService extends PostingService<AnswerPost> {
    protected http = inject(HttpClient);

    public resourceUrl = 'api/communication/courses/';

    constructor() {
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
     * gets source answer posts(original (forwarded) answer posts in posts) of posts
     * @param {number} courseId
     * @param {number[]} postIds
     * @return {Observable<AnswerPost[]>}
     */
    getSourceAnswerPostsByIds(courseId: number, answerPostIds: number[]): Observable<AnswerPost[]> {
        const params = new HttpParams().set('answerPostIds', answerPostIds.join(','));
        return this.http
            .get<AnswerPost[]>(`api/communication/courses/${courseId}/answer-messages-source-posts`, { params, observe: 'response' })
            .pipe(map((response) => response.body!));
    }

    private static getResourceEndpoint(param: AnswerPost): string {
        if (param.post?.conversation) {
            return '/answer-messages';
        } else {
            return '/answer-posts';
        }
    }
}
