import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';

import { SERVER_API_URL } from 'app/app.constants';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';

type EntityResponseType = HttpResponse<AnswerPost>;
type EntityArrayResponseType = HttpResponse<AnswerPost[]>;

@Injectable({ providedIn: 'root' })
export class PostingService {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * create posting (either Post or AnswerPost)
     * @param {number} courseId
     * @param {Post | AnswerPost} posting
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, posting: Post | AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(posting);
        const endpoint = posting instanceof Post ? 'posts' : 'answer-posts';
        return this.http
            .post<AnswerPost>(`${this.resourceUrl}${courseId}/${endpoint}`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update posting (either Post or AnswerPost)
     * @param {number} courseId
     * @param {Post | AnswerPost} posting
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, posting: Post | AnswerPost): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(posting);
        const endpoint = posting instanceof Post ? 'posts' : 'answer-posts';
        return this.http
            .put<AnswerPost>(`${this.resourceUrl}${courseId}/${endpoint}`, copy, { observe: 'response' })
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
     * convert the date from the client
     * @param   {Post | AnswerPost} posting
     * @return  {Post | AnswerPost}
     */
    protected convertDateFromClient(posting: Post | AnswerPost): Post | AnswerPost {
        return Object.assign({}, posting, {
            creationDate: posting.creationDate && moment(posting.creationDate).isValid() ? moment(posting.creationDate).toJSON() : undefined,
        });
    }

    /**
     * convert the date from the server
     * @param   {EntityResponseType} res
     * @return  {Post | AnswerPost}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? moment(res.body.creationDate) : undefined;
        }
        return res;
    }

    /**
     * converts the date in array of postings (either Post or AnswerPost) from the server
     * @param   {EntityArrayResponseType} res
     * @return  {EntityArrayResponseType}
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((posting: Post | AnswerPost) => {
                posting.creationDate = posting.creationDate ? moment(posting.creationDate) : undefined;
            });
        }
        return res;
    }

    /**
     * update the votes of a post
     * @param {number} courseId
     * @param {number} postId
     * @param {number} voteChange
     * @return {Observable<EntityResponseType>}
     */
    updateVotes(courseId: number, postId: number, voteChange: number): Observable<EntityResponseType> {
        return this.http
            .put(`${this.resourceUrl}${courseId}/posts/${postId}/votes`, voteChange, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * find all posts for id of course
     * @param {number} courseId
     * @return {Observable<EntityArrayResponseType>}
     */
    findPostsForCourse(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<Post[]>(`api/courses/${courseId}/posts`, { observe: 'response' }).pipe(map((res: EntityArrayResponseType) => this.convertDateArrayFromServer(res)));
    }
}
