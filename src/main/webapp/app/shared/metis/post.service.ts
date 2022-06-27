import { Injectable } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import dayjs from 'dayjs/esm';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingService } from 'app/shared/metis/posting.service';
import { DisplayPriority, PostContextFilter } from 'app/shared/metis/metis.util';

type EntityResponseType = HttpResponse<Post>;
type EntityArrayResponseType = HttpResponse<Post[]>;

@Injectable({ providedIn: 'root' })
export class PostService extends PostingService<Post> {
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
     * gets all posts for course by its id, filtered by context if PostContextFilter is passed
     * a context to filter posts for can be a course-wide topic, a lecture, or an exercise within a course
     * @param {number} courseId
     * @param {PostContextFilter} postContextFilter
     * @return {Observable<EntityArrayResponseType>}
     */
    getPosts(courseId: number, postContextFilter: PostContextFilter): Observable<EntityArrayResponseType> {
        let params = new HttpParams();

        if (postContextFilter.postSortCriterion) {
            params = params.set('postSortCriterion', postContextFilter.postSortCriterion.toString());
        }
        if (postContextFilter.sortingOrder) {
            params = params.set('sortingOrder', postContextFilter.sortingOrder.toString());
        }
        if (postContextFilter.courseWideContext) {
            params = params.set('courseWideContext', postContextFilter.courseWideContext.toString());
        }
        if (postContextFilter.lectureId) {
            params = params.set('lectureId', postContextFilter.lectureId.toString());
        }
        if (postContextFilter.exerciseId) {
            params = params.set('exerciseId', postContextFilter.exerciseId.toString());
        }
        if (postContextFilter.plagiarismCaseId) {
            params = params.set('plagiarismCaseId', postContextFilter.plagiarismCaseId.toString());
        }
        if (postContextFilter.searchText) {
            params = params.set('searchText', postContextFilter.searchText.toString());
        }
        if (postContextFilter.filterToUnresolved) {
            params = params.set('filterToUnresolved', postContextFilter.filterToUnresolved);
        }
        if (postContextFilter.filterToOwn) {
            params = params.set('filterToOwn', postContextFilter.filterToOwn);
        }
        if (postContextFilter.filterToAnsweredOrReacted) {
            params = params.set('filterToAnsweredOrReacted', postContextFilter.filterToAnsweredOrReacted);
        }
        if (!!postContextFilter.pagingEnabled) {
            params = params.set('pagingEnabled', postContextFilter.pagingEnabled);
            params = params.set('page', postContextFilter.page!);
            params = params.set('size', postContextFilter.pageSize!);
        }
        return this.http
            .get<Post[]>(`${this.resourceUrl}${courseId}/posts`, {
                params,
                observe: 'response',
            })
            .pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * gets all tags for course
     * @param {number} courseId
     * @return {Observable<string[]>}
     */
    getAllPostTagsByCourseId(courseId: number): Observable<HttpResponse<string[]>> {
        return this.http.get<string[]>(`${this.resourceUrl}${courseId}/posts/tags`, { observe: 'response' });
    }

    /**
     * updates a post
     * @param {number} courseId
     * @param {Post} post
     * @return {Observable<EntityResponseType>}
     */
    update(courseId: number, post: Post): Observable<EntityResponseType> {
        const copy = this.convertDateFromClient(post);
        return this.http.put<Post>(`${this.resourceUrl}${courseId}/posts/${post.id}`, copy, { observe: 'response' }).pipe(map(this.convertDateFromServer));
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
            .put(`${this.resourceUrl}${courseId}/posts/${postId}/display-priority`, {}, { params: { displayPriority }, observe: 'response' })
            .pipe(map(this.convertDateFromServer));
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
     * determines similar posts in a course
     * @param {Post} tempPost
     * @param {number} courseId
     * @return {Observable<HttpResponse<void>>}
     */
    computeSimilarityScoresWithCoursePosts(tempPost: Post, courseId: number): Observable<EntityArrayResponseType> {
        const copy = this.convertDateFromClient(tempPost);
        return this.http.post<Post[]>(`${this.resourceUrl}${courseId}/posts/similarity-check`, copy, { observe: 'response' }).pipe(map(this.convertDateArrayFromServer));
    }

    /**
     * takes an array of posts and converts the date from the server
     * @param   {HttpResponse<Post[]>} res
     * @return  {HttpResponse<Post[]>}
     */
    convertDateArrayFromServer(res: HttpResponse<Post[]>): HttpResponse<Post[]> {
        if (res.body) {
            res.body.forEach((post: Post) => {
                post.creationDate = post.creationDate ? dayjs(post.creationDate) : undefined;
                post.answers?.forEach((answer: AnswerPost) => {
                    answer.creationDate = answer.creationDate ? dayjs(answer.creationDate) : undefined;
                });
            });
        }
        return res;
    }
}
