import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { Post } from 'app/entities/metis/post.model';
import { PostingsService } from 'app/shared/metis/postings.service';
import { DisplayPriority, PostContextFilter } from 'app/shared/metis/metis.util';

type EntityResponseType = HttpResponse<Post>;
type EntityArrayResponseType = HttpResponse<Post[]>;

@Injectable({ providedIn: 'root' })
export class PostService extends PostingsService<Post> {
    public resourceUrl = SERVER_API_URL + 'api/courses/';

    constructor(protected http: HttpClient) {
        super();
    }

    /**
     * creates a post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<EntityResponseType>}
     */
    create(courseId: number, post: Post): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(post);
        return this.http.post<Post>(`${this.resourceUrl}${courseId}/posts`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * updates a post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, post: Post): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(post);
        return this.http.put<Post>(`${this.resourceUrl}${courseId}/posts`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
    }

    /**
     * updates the display priority of a post
     * @param {number} courseId
     * @param {number} postId
     * @param {DisplayPriority} displayPriority
     * @return {Observable<EntityResponseType>}
     */
    updatePostDisplayPriority(courseId: number, postId: number, displayPriority: DisplayPriority): Observable<EntityResponseType> {
        return this.http
            .put(
                `${this.resourceUrl}${courseId}/posts/${postId}/display-priority`,
                {},
                {
                    params: { displayPriority },
                    observe: 'response',
                },
            )
            .pipe(map(this.convertDateFromServer));
    }

    /**
     * gets all posts for course by its id, filtered by context if PostContextFilter is passed
     * a context to filter posts for can be a course-wide topic, a lecture, or an exercise within a course
     * @param {number} courseId
     * @param {PostContextFilter} postContextFilter
     * @return {Observable<EntityArrayResponseType>}
     */
    getPosts(courseId: number, postContextFilter: PostContextFilter): Observable<EntityArrayResponseType> {
        let params = new HttpParams();
        if (postContextFilter.courseWideContext) {
            params = params.set('courseWideContext', postContextFilter.courseWideContext.toString());
        }
        if (postContextFilter.lectureId) {
            params = params.set('lectureId', postContextFilter.lectureId.toString());
        }
        if (postContextFilter.exerciseId) {
            console.log(postContextFilter.exerciseId.toString());
            params = params.set('exerciseId', postContextFilter.exerciseId.toString());
        }
        return this.http
            .get<Post[]>(`${this.resourceUrl}${courseId}/posts`, {
                params,
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * gets all posts for course by its id
     * @param {number} courseId
     * @return {Observable<EntityArrayResponseType>}
     */
    getAllPostsByCourseId(courseId: number): Observable<EntityArrayResponseType> {
        return this.http.get<Post[]>(`${this.resourceUrl}${courseId}/posts`, { observe: 'response' }).pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * gets all posts for a lecture in a certain course by its id
     * @param {number} courseId
     * @param {number} lectureId
     * @return {Observable<EntityArrayResponseType>}
     */
    getAllPostsByLectureId(courseId: number, lectureId: number): Observable<EntityArrayResponseType> {
        return this.http.get<Post[]>(`${this.resourceUrl}${courseId}/lectures/${lectureId}/posts`, { observe: 'response' }).pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * gets all posts for an exercise in a certain course by its id
     * @param {number} courseId
     * @param {number} exerciserId
     * @return {Observable<EntityArrayResponseType>}
     */
    getAllPostsByExerciseId(courseId: number, exerciserId: number): Observable<EntityArrayResponseType> {
        return this.http.get<Post[]>(`${this.resourceUrl}${courseId}/exercises/${exerciserId}/posts`, { observe: 'response' }).pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * deletes a post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<HttpResponse<void>>}
     */
    delete(courseId: number, post: Post): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.resourceUrl}${courseId}/posts/${post.id}`, { observe: 'response' });
    }

    /**
     * gets all tags for course
     * @param {number} courseId
     * @return {Observable<string[]>}
     */
    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}${courseId}/posts/tags`, { observe: 'response' });
    }
}
