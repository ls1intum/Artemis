import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { Reaction } from 'app/entities/metis/reaction.model';

export class MockMetisService {
    get tags(): Observable<string[]> {
        return of(['tag1', 'tag2']);
    }

    getUser(): User {
        return { id: 1, name: 'username', login: 'login' } as User;
    }

    createPost = (post: Post): Observable<Post> => of(post);
    createAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    createReaction(reaction: Reaction): Observable<Reaction> {
        return of(reaction);
    }

    updatePost = (post: Post): Observable<Post> => of(post);
    updateAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    deletePost(post: Post): void {}

    deleteAnswerPost(answerPost: AnswerPost): void {}

    deleteReaction(reaction: Reaction): void {}

    metisUserIsAtLeastTutorInCourse(): boolean {
        return true;
    }

    metisUserIsAuthorOfPosting(posting: Posting): boolean {
        return true;
    }
}
