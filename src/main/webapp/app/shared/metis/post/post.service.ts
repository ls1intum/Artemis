import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map, filter } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { Post } from 'app/entities/metis/post.model';
import { PostingsService } from 'app/shared/metis/postings.service';

type EntityResponseType = HttpResponse<Post>;
type EntityArrayResponseType = HttpResponse<Post[]>;

@Injectable({ providedIn: 'root' })
export class PostService extends PostingsService<Post> {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {
        super();
    }

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
     * delete post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<HttpResponse<any>>}
     */
    delete(courseId: number, post: Post): Observable<HttpResponse<any>> {
        return this.http.delete<any>(`${this.resourceUrl}${courseId}/posts/${post.id}`, { observe: 'response' });
    }

    /**
     * get all tags for course
     * @param {number} courseId
     * @return {Observable<string[]>}
     */
    getAllPostTags(courseId: number): Observable<string[]> {
        return this.http.get<string[]>(`api/courses/${courseId}/posts/tags`, { observe: 'response' }).pipe(
            map((tagsResponse: HttpResponse<string[]>) => {
                return tagsResponse.body!.filter((t) => !!t);
            }),
        );
    }
}
