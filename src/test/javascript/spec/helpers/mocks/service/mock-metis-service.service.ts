import { Params } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Course } from 'app/entities/course.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ContextInformation, PageType, PostContextFilter, RouteComponents } from 'app/shared/metis/metis.util';
import { Observable, of } from 'rxjs';
import { metisCourse, metisCoursePosts, metisTags, metisUser1 } from '../../sample/metis-sample-data';

let pageType: PageType;

export class MockMetisService {
    get tags(): Observable<string[]> {
        return of(metisTags);
    }

    get posts(): Observable<Post[]> {
        return of(metisCoursePosts);
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

    createReaction = (reaction: Reaction): Observable<Reaction> => of(reaction);

    updatePost = (post: Post): Observable<Post> => of(post);

    updateAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of(answerPost);

    deletePost(post: Post): void {}

    deleteAnswerPost(answerPost: AnswerPost): void {}

    deleteReaction(reaction: Reaction): void {}

    metisUserIsAtLeastTutorInCourse(): boolean {
        return true;
    }

    metisUserIsAtLeastInstructorInCourse(): boolean {
        return true;
    }

    metisUserIsAuthorOfPosting(posting: Posting): boolean {
        return true;
    }

    getFilteredPosts(postContextFilter: PostContextFilter, forceUpdate = true): void {}

    isPostResolved(post: Post) {
        return false;
    }

    getLinkForPost(post?: Post): RouteComponents {
        if (post?.lecture) {
            return ['/courses', metisCourse.id!, 'lectures', post.lecture.id!];
        }
        if (post?.exercise) {
            return ['/courses', metisCourse.id!, 'exercises', post.exercise.id!];
        }
        return ['/courses', metisCourse.id!, 'discussion'];
    }

    getLinkForExercise(exerciseId: string): string {
        return `/courses/${metisCourse.id}/exercises/${exerciseId}`;
    }

    getLinkForLecture(lectureId: string): string {
        return '/courses/' + metisCourse.id + '/lectures/' + lectureId;
    }

    getContextInformation(post: Post): ContextInformation {
        let routerLinkComponents = undefined;
        let displayName;
        if (post.exercise) {
            displayName = post.exercise.title!;
            routerLinkComponents = ['/courses', metisCourse.id!, 'exercises', post.exercise.id!];
        } else if (post.lecture) {
            displayName = post.lecture.title!;
            routerLinkComponents = ['/courses', metisCourse.id!, 'lectures', post.lecture.id!];
            // course-wide topics are not linked
        } else {
            displayName = 'some context';
        }
        return { routerLinkComponents, displayName };
    }

    getQueryParamsForPost(post: Post): Params {
        const params: Params = {};
        if (post.courseWideContext) {
            params.searchText = `#${post.id}`;
        } else {
            params.postId = post.id;
        }
        return params;
    }

    getSimilarPosts(title: string): Observable<Post[]> {
        return of(metisCoursePosts.slice(0, 5));
    }
}
