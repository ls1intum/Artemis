import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Post } from 'app/entities/metis/post.model';
import { Posting, PostingType, SavedPostStatus } from 'app/entities/metis/posting.model';
import { map } from 'rxjs/operators';
import { convertDateFromServer } from 'app/utils/date.utils';
import { ConversationType } from 'app/entities/metis/conversation/conversation.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Injectable({
    providedIn: 'root',
})
export class SavedPostService {
    private resourceUrl = 'api/communication/saved-posts/';

    private readonly http: HttpClient = inject(HttpClient);

    /**
     * saves a post
     * @param {Posting} post
     * @return {Observable<Object>}
     */
    public savePost(post: Posting): Observable<object> {
        const type = this.getPostingType(post).toString();

        return this.http.post(`${this.resourceUrl}${post.id}/${type}`, {}, { observe: 'response' });
    }

    /**
     * un-saves a post
     * @param {Posting} post
     * @return {Observable<Object>}
     */
    public removeSavedPost(post: Posting): Observable<object> {
        const type = this.getPostingType(post).toString();

        return this.http.delete(`${this.resourceUrl}${post.id}/${type}`, { observe: 'response' });
    }

    /**
     * updates a posts status
     * @param {Posting} post
     * @param {SavedPostStatus} status of a post (progress, archived, completed)
     * @return {Observable<Object>}
     */
    public changeSavedPostStatus(post: Posting, status: SavedPostStatus): Observable<object> {
        const type = this.getPostingType(post).toString();

        return this.http.put(`${this.resourceUrl}${post.id}/${type}?status=${status.toString()}`, null, { observe: 'response' });
    }

    /**
     * Fetches the saved postings for a given status
     * @param {number} courseId
     * @param {SavedPostStatus} status
     * @return {Observable<Posting[]>}
     */
    public fetchSavedPosts(courseId: number, status: SavedPostStatus): Observable<HttpResponse<Posting[]>> {
        return this.http.get(`${this.resourceUrl}${courseId}/${status.toString()}`, { observe: 'response' }).pipe(map(this.convertPostResponseFromServer));
    }

    /**
     * Converts posting to the corresponding type
     * @param {Posting} post to convert
     * @return {Post|AnswerPost}
     */
    public convertPostingToCorrespondingType(post: Posting) {
        return Object.assign((post.postingType as PostingType) === PostingType.POST ? new Post() : new AnswerPost(), post);
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
