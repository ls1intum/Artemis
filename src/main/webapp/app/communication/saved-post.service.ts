import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { convertDateFromServer } from 'app/shared/util/date.utils';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

@Injectable({
    providedIn: 'root',
})
export class SavedPostService {
    private resourceUrl = 'api/communication/saved-posts';

    private readonly http: HttpClient = inject(HttpClient);

    /**
     * saves a post
     * @param {Posting} post
     * @return {Observable<Object>}
     */
    public savePost(post: Posting): Observable<object> {
        const params = new HttpParams().set('type', this.getPostingType(post).toString().toLowerCase());
        return this.http.post(`${this.resourceUrl}/${post.id}`, {}, { observe: 'response', params });
    }

    /**
     * un-saves a post
     * @param {Posting} post
     * @return {Observable<Object>}
     */
    public removeSavedPost(post: Posting): Observable<object> {
        const params = new HttpParams().set('type', this.getPostingType(post).toString().toLowerCase());
        return this.http.delete(`${this.resourceUrl}/${post.id}`, { observe: 'response', params });
    }

    /**
     * updates a posts status
     * Note: it's best practice to send query params in lower case, the server can handle this
     * @param post to update
     * @param status of a post (progress, archived, completed)
     * @return an observable that can be used by the caller
     */
    public changeSavedPostStatus(post: Posting, status: SavedPostStatus): Observable<object> {
        const params = new HttpParams().set('type', this.getPostingType(post).toString().toLowerCase()).set('status', status.toString().toLowerCase());
        return this.http.put(`${this.resourceUrl}/${post.id}`, null, { observe: 'response', params });
    }

    /**
     * Fetches the saved postings for a given status
     * Note: it's best practice to send query params in lower case, the server can handle this
     * @param courseId the courseId of the postings
     * @param status of the saved post (progress, archived, completed)
     * @return an observable that can be used by the caller
     */
    public fetchSavedPosts(courseId: number, status: SavedPostStatus): Observable<HttpResponse<Posting[]>> {
        const params = new HttpParams().set('status', status.toString().toLowerCase()).set('courseId', courseId.toString());
        return this.http.get(`${this.resourceUrl}`, { observe: 'response', params }).pipe(map(this.convertPostResponseFromServer));
    }

    /**
     * Converts posting to the corresponding type
     * @param post to convert
     * @return the converted post or answer post
     */
    public convertPostingToCorrespondingType(post: Posting) {
        return Object.assign(post.postingType === PostingType.POST ? new Post() : new AnswerPost(), post);
    }

    /**
     * takes an array of postings and converts the date from the server
     * @param   {HttpResponse<Posting[]>} res
     * @return  {HttpResponse<Posting[]>}
     */
    private convertPostResponseFromServer(res: HttpResponse<Posting[]>): HttpResponse<Posting[]> {
        if (res.body) {
            res.body.forEach((post) => {
                post.creationDate = convertDateFromServer(post.creationDate);
                post.updatedDate = convertDateFromServer(post.updatedDate);
                if (post.conversation?.type !== undefined) {
                    post.conversation.type = post.conversation.type.toLowerCase() as ConversationType;
                }
            });
        }
        return res;
    }

    /**
     * Retrieves the proper enum type for a posting
     * @param {Posting} post
     * @return {PostingType}
     */
    private getPostingType(post: Posting): PostingType {
        return post instanceof Post ? PostingType.POST : PostingType.ANSWER;
    }
}
