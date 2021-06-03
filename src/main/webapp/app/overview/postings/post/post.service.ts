import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import * as moment from 'moment';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { Post } from 'app/entities/metis/post.model';

type EntityResponseType = HttpResponse<Post>;
type EntityArrayResponseType = HttpResponse<Post[]>;

@Injectable({ providedIn: 'root' })
export class PostService {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {}

    /**
     * create a post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, post: Post): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(post);
        return this.http.post<Post>(`${this.resourceUrl}${courseId}/posts`, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
    }

    /**
     * update the post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, post: Post): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(post);
        return this.http.put<Post>(`${this.resourceUrl}${courseId}/posts`, copy, { observe: 'response' }).pipe(map((res: EntityResponseType) => this.convertDateFromServer(res)));
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

    /**
     * delete post by id
     * @param {number} courseId
     * @param {number} postId
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, postId: number): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}${courseId}/posts/${postId}`, { observe: 'response' });
    }

    /**
     * Takes a post and converts the date from the client
     * @param   {Post} post
     * @return  {Post}
     */
    protected convertDateFromClient(post: Post): Post {
        return Object.assign({}, post, {
            creationDate: post.creationDate && moment(post.creationDate).isValid() ? moment(post.creationDate).toJSON() : undefined,
        });
    }

    /**
     * Takes a post and converts the date from the server
     * @param   {EntityResponseType} res
     * @return  {Post}
     */
    protected convertDateFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            res.body.creationDate = res.body.creationDate ? moment(res.body.creationDate) : undefined;
        }
        return res;
    }

    /**
     * Takes an array of posts and converts the date from the server
     * @param   {EntityArrayResponseType} res
     * @return  {EntityArrayResponseType}
     */
    protected convertDateArrayFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((post: Post) => {
                post.creationDate = post.creationDate ? moment(post.creationDate) : undefined;
            });
        }
        return res;
    }
}
