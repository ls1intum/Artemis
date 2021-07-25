import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';

export class MockMetisService {
    get tags(): Observable<string[]> {
        return of(['tag1', 'tag2']);
    }

    createPost = (post: Post): Observable<Post> => of(post);
    createAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    updatePost = (post: Post): Observable<Post> => of(post);
    updateAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    deletePost(post: Post): void {}

    deleteAnswerPost(answerPost: AnswerPost): void {}

    metisUserIsAtLeastTutorInCourse(): boolean {
        return true;
    }

    metisUserIsAuthorOfPosting(): boolean {
        return true;
    }
}
