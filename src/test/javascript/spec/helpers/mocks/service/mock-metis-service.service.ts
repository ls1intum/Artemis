import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PageType, PostContextFilter } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';

let pageType: PageType;
const defaultExercise = { id: 1, type: ExerciseType.TEXT, title: 'default exercise' } as Exercise;
const defaultLecture = { id: 1, title: 'default lecture' } as Exercise;

export class MockMetisService {
    get tags(): Observable<string[]> {
        return of(['tag1', 'tag2']);
    }

    getUser(): User {
        return { id: 1, name: 'username', login: 'login' } as User;
    }

    getCourse(): Course {
        return { id: 1, exercises: [defaultExercise], lectures: [defaultLecture] } as Course;
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
