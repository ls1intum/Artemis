import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

type EntityResponseType = HttpResponse<AnswerPost>;
type EntityArrayResponseType = HttpResponse<AnswerPost[]>;

@Injectable({ providedIn: 'root' })
export class AnswerPostService {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * create answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(answerPost);
        return this.http
            .post<AnswerPost>(`${this.resourceUrl}${courseId}/answer-posts`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update answerPost
     * @param {number} courseId
     * @param {AnswerPost} answerPost
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, answerPost: AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(answerPost);
        return this.http
            .put<AnswerPost>(`${this.resourceUrl}${courseId}/answer-posts`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * delete answerPost by id
     * @param {number} courseId
     * @param {number} answerId
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, answerId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}${courseId}/answer-posts/${answerId}`, { observe: 'response' });
    }

    /**
     * Takes a answerPost and converts the date from the client
     * @param   {AnswerPost} answerPost
     * @return  {AnswerPost}
     */
    protected convertDateFromClient(answerPost: AnswerPost): AnswerPost {
        return Object.assign({}, answerPost, {
            creationDate: answerPost.creationDate && moment(answerPost.creationDate).isValid() ? moment(answerPost.creationDate).toJSON() : undefined,
        });
    }

    /**
     * Takes a answerPost and converts the date from the server
     * @param   {EntityResponseType} res
     * @return  {AnswerPost}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? moment(res.body.creationDate) : undefined;
        }
        return res;
    }

    /**
     * Takes an array of answerPosts and converts the date from the server
     * @param   {EntityArrayResponseType} res
     * @return  {EntityArrayResponseType}
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((answerPost: AnswerPost) => {
                answerPost.creationDate = answerPost.creationDate ? moment(answerPost.creationDate) : undefined;
            });
        }
        return res;
    }
}
