import { BehaviorSubject, Observable, of } from 'rxjs';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { User } from 'app/account/user/user.model';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { ContextInformation, PageType, PostContextFilter, RouteComponents } from 'app/communication/communication.util';
import { Course } from 'app/course/shared/entities/course.model';
import { Params } from '@angular/router';
import { communicationCourse, communicationCoursePosts, communicationTags, communicationUser1 } from '../../sample/communication-sample-data';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Faq } from 'app/communication/shared/entities/faq.model';

let pageType: PageType;

export class MockCommunicationService {
    currentConversation = undefined;
    private faqsSubject = new BehaviorSubject<Faq[]>([]);

    get tags(): Observable<string[]> {
        return of(communicationTags);
    }

    get posts(): Observable<Post[]> {
        return of(communicationCoursePosts);
    }

    getUser(): User {
        return communicationUser1;
    }

    getCourse(): Course {
        return communicationCourse;
    }

    getFaqs(): Observable<Faq[]> {
        return this.faqsSubject.asObservable();
    }

    setFaqs(faqs: Faq[]): void {
        this.faqsSubject.next(faqs);
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

    deleteAnswerPost(answerPost: AnswerPost): Observable<void> {
        return of(undefined);
    }

    verifyAnswerPost(answerPost: AnswerPost, content?: string): Observable<AnswerPost> {
        return of(answerPost);
    }

    deleteReaction(reaction: Reaction): void {}

    resetCachedPosts(): void {}

    currentUserIsAtLeastTutorInCourse(): boolean {
        return true;
    }

    currentUserIsAtLeastInstructorInCourse(): boolean {
        return true;
    }

    currentUserIsAuthorOfPosting(posting: Posting): boolean {
        return true;
    }

    getFilteredPosts(postContextFilter: PostContextFilter, forceUpdate = true): void {}

    isPostResolved(post: Post) {
        return false;
    }

    getLinkForPost(post?: Post): RouteComponents {
        return ['/courses', communicationCourse.id!, 'discussion'];
    }

    getLinkForExercise(exerciseId: string): string {
        return `/courses/${communicationCourse.id}/exercises/${exerciseId}`;
    }

    getLinkForLecture(lectureId: string): string {
        return '/courses/' + communicationCourse.id + '/lectures/' + lectureId;
    }

    getLinkForExam(examId: string): string {
        return '/courses/' + communicationCourse.id + '/exams/' + examId;
    }

    getLinkForFaq(): string {
        return `/courses/${this.getCourse().id}/faq`;
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
        const routerLinkComponents = ['/courses', post.conversation?.course?.id ?? 1, 'communication'];
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
        return of(communicationCoursePosts.slice(0, 5));
    }

    setCourse(course: Course | undefined): void {}
}
