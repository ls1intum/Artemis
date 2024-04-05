import { Observable, of } from 'rxjs';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { Posting } from 'app/entities/metis/posting.model';
import { User } from 'app/core/user/user.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ContextInformation, PageType, PostContextFilter, RouteComponents } from 'app/shared/metis/metis.util';
import { Course } from 'app/entities/course.model';
import { Params } from '@angular/router';
import { metisCourse, metisCoursePosts, metisTags, metisUser1 } from '../../sample/metis-sample-data';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';

let pageType: PageType;

export class MockMetisService {
    currentConversation = undefined;

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

    getCurrentConversation(): ConversationDTO | undefined {
        return this.currentConversation;
    }

    createPost = (post: Post): Observable<Post> => of(post);

    createAnswerPost = (answerPost: AnswerPost): Observable<AnswerPost> => of({ ...answerPost });

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
        return ['/courses', metisCourse.id!, 'discussion'];
    }

    getLinkForExercise(exerciseId: string): string {
        return `/courses/${metisCourse.id}/exercises/${exerciseId}`;
    }

    getLinkForLecture(lectureId: string): string {
        return '/courses/' + metisCourse.id + '/lectures/' + lectureId;
    }

    getLinkForExam(examId: string): string {
        return '/courses/' + metisCourse.id + '/exams/' + examId;
    }

    getLinkForChannelSubType(channel?: ChannelDTO): string | undefined {
        const referenceId = channel?.subTypeReferenceId?.toString();
        if (!referenceId) {
            return undefined;
        }

        switch (channel?.subType) {
            case ChannelSubType.EXERCISE:
                return this.getLinkForExercise(referenceId);
            case ChannelSubType.LECTURE:
                return this.getLinkForLecture(referenceId);
            case ChannelSubType.EXAM:
                return this.getLinkForExam(referenceId);
            default:
                return undefined;
        }
    }

    getContextInformation(post: Post): ContextInformation {
        const routerLinkComponents = ['/courses', post.conversation?.course?.id ?? 1, 'messages'];
        const queryParams = { conversationId: post.conversation?.id };
        const displayName = getAsChannelDTO(post.conversation)?.name ?? 'some context';
        return { routerLinkComponents, displayName, queryParams };
    }

    getQueryParamsForPost(post: Post): Params {
        const params: Params = {};
        params.searchText = `#${post.id}`;
        return params;
    }

    getSimilarPosts(title: string): Observable<Post[]> {
        return of(metisCoursePosts.slice(0, 5));
    }

    setCourse(course: Course | undefined): void {}
}
