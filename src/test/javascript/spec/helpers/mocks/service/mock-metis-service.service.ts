import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PageType, PostContextFilter } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { metisCourse, metisTags, metisUser1 } from '../../sample/metis-sample-data';

let pageType: PageType;

export class MockMetisService {
    get tags(): Observable<string[]> {
        return of(metisTags);
    }

    getUser(): User {
        return metisUser1;
    }

    getCourse(): Course {
        return metisCourse;
    }

    getPageType(): PageType {
        return pageType;
    }

    setPageType(newPageType: PageType) {
        pageType = newPageType;
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

    getFilteredPosts(postContextFilter: PostContextFilter, forceUpdate = true): void {}
}
