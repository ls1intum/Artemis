import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { HttpResponse } from '@angular/common/http';
import { Post } from 'app/entities/metis/post.model';

export class MockMetisService {
    createPost = (post: Post): Observable<Post> => of(post);
    createAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    updatePost = (post: Post): Observable<Post> => of(post);
    updateAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    deletePost(post: Post): Observable<HttpResponse<any>> {
        return of();
    }

    deleteAnswerPost(answerPost: AnswerPost): Observable<HttpResponse<any>> {
        return of();
    }

    metisUserIsAtLeastTutorInCourse(): Observable<HttpResponse<any>> {
        return of();
    }

    metisUserIsAuthorOfPosting(): Observable<HttpResponse<any>> {
        return of();
    }
}
