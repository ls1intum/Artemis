import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { BehaviorSubject, Observable, ReplaySubject, Subscription, map, tap } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';
import { Injectable, OnDestroy, inject } from '@angular/core';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import {
    ContextInformation,
    DisplayPriority,
    MetisPostAction,
    MetisWebsocketChannelPrefix,
    PageType,
    PostContextFilter,
    RouteComponents,
    SortDirection,
} from 'app/shared/metis/metis.util';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Params } from '@angular/router';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import dayjs from 'dayjs/esm';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Conversation, ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { FaqService } from 'app/faq/faq.service';
import { Faq } from 'app/entities/faq.model';

@Injectable()
export class MetisService implements OnDestroy {
    private posts$: ReplaySubject<Post[]> = new ReplaySubject<Post[]>(1);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private totalNumberOfPosts$: ReplaySubject<number> = new ReplaySubject<number>(1);

    private currentPostContextFilter: PostContextFilter = {};
    private currentConversation?: ConversationDTO = undefined;
    private user: User;
    private pageType: PageType;
    private course: Course;
    private courseId: number;
    private cachedPosts: Post[] = [];
    private cachedTotalNumberOfPosts: number;
    private subscriptionChannel?: string;

    private courseWideTopicSubscription: Subscription;

    private faqService = inject(FaqService);

    constructor(
        protected postService: PostService,
        protected answerPostService: AnswerPostService,
        protected reactionService: ReactionService,
        protected accountService: AccountService,
        protected exerciseService: ExerciseService,
        private jhiWebsocketService: JhiWebsocketService,
        private conversationService: ConversationService,
        notificationService: NotificationService,
    ) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });

        this.courseWideTopicSubscription = notificationService.newOrUpdatedMessage.subscribe(this.handleNewOrUpdatedMessage);
    }

    get posts(): Observable<Post[]> {
        return this.posts$.asObservable();
    }

    get tags(): Observable<string[]> {
        return this.tags$.asObservable();
    }

    get totalNumberOfPosts(): Observable<number> {
        return this.totalNumberOfPosts$.asObservable();
    }

    getCurrentConversation(): ConversationDTO | undefined {
        return this.currentConversation;
    }

    static getLinkForLecturePost(courseId: number, lectureId: number): RouteComponents {
        return ['/courses', courseId, 'lectures', lectureId];
    }

    static getLinkForExercisePost(courseId: number, exerciseId: number): RouteComponents {
        return ['/courses', courseId, 'exercises', exerciseId];
    }

    static getLinkForCoursePost(courseId: number): RouteComponents {
        return ['/courses', courseId, 'discussion'];
    }

    static getLinkForPlagiarismCasePost(courseId: number, plagiarismCaseId: number): RouteComponents {
        return ['/courses', courseId, 'plagiarism-cases', plagiarismCaseId];
    }

    static getQueryParamsForCoursePost(postId: number): Params {
        const params: Params = {};
        params.searchText = `#${postId}`;
        return params;
    }

    static getQueryParamsForLectureOrExercisePost(postId: number): Params {
        const params: Params = {};
        params.postId = postId;
        return params;
    }

    ngOnDestroy(): void {
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
        }
        this.courseWideTopicSubscription.unsubscribe();
    }

    getPageType(): PageType {
        return this.pageType;
    }

    setPageType(pageType: PageType) {
        this.pageType = pageType;
    }

    getUser(): User {
        return this.user;
    }

    getCourse(): Course {
        return this.course;
    }

    /**
     * set course property before using metis service
     * @param {Course} course in which the metis service is used
     */
    setCourse(course: Course | undefined): void {
        if (course && (this.courseId === undefined || this.courseId !== course.id)) {
            this.courseId = course.id!;
            this.course = course;
        }
    }

    /**
     * set course property before using metis service
     * @param {Course} course in which the metis service is used
     */
    setFaqs(course: Course | undefined): void {
        if (course) {
            this.faqService
                .findAllByCourseId(this.courseId)
                .pipe(map((res: HttpResponse<Faq[]>) => res.body))
                .subscribe({
                    next: (res: Faq[]) => {
                        course.faqs = res;
                    },
                });
        }
    }

    /**
     * to be used to set posts from outside
     * @param {Post[]} posts that are managed by metis service
     */
    setPosts(posts: Post[]): void {
        this.posts$.next(posts);
    }

    /**
     * fetches all posts for a course, optionally fetching posts only for a certain context, i.e. a lecture, exercise or specified course-wide-context,
     * informs all components that subscribed on posts by sending the newly fetched posts
     * @param postContextFilter criteria to filter course posts with (lecture, exercise, course-wide context)
     * @param forceUpdate if true, forces a re-fetch even if filter property did not change
     * @param conversation active conversation if available
     */
    getFilteredPosts(postContextFilter: PostContextFilter, forceUpdate = true, conversation: ConversationDTO | undefined = undefined): void {
        // check if the post context did change
        if (
            forceUpdate ||
            postContextFilter?.courseId !== this.currentPostContextFilter?.courseId ||
            postContextFilter?.conversationId !== this.currentPostContextFilter?.conversationId ||
            this.hasDifferentContexts(postContextFilter) ||
            postContextFilter?.plagiarismCaseId !== this.currentPostContextFilter?.plagiarismCaseId ||
            postContextFilter?.page !== this.currentPostContextFilter?.page
        ) {
            this.currentPostContextFilter = postContextFilter;
            this.currentConversation = conversation;
            this.postService.getPosts(this.courseId, postContextFilter).subscribe((res) => {
                if (!forceUpdate && (PageType.OVERVIEW === this.pageType || PageType.PAGE_SECTION === this.pageType)) {
                    // if infinite scroll enabled, add fetched posts to the end of cachedPosts
                    this.cachedPosts.push(...res.body!);
                } else {
                    // if the context changed, we need to fetch posts and dismiss cached posts
                    this.cachedPosts = res.body!;
                }
                this.cachedTotalNumberOfPosts = Number(res.headers.get('X-Total-Count') ?? '0');
                this.posts$.next(this.cachedPosts);
                this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                this.createSubscriptionFromPostContextFilter();
            });
        } else {
            // if we do not require force update, e.g. because only the post title, tag or content changed,
            // we can emit the previously cached posts
            this.posts$.next(this.cachedPosts);
            this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
        }
    }

    /**
     * creates a new post by invoking the post service
     * @param {Post} post to be created
     * @return {Observable<Post>} created post
     */
    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(
            map((res: HttpResponse<Post>) => res.body!),
            tap((createdPost: Post) => {
                const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === createdPost.id);
                // Update the cached posts after successfully creating a new post if it is not already cached (can happen if the WebSocket message arrives before the HTTP response)
                if (indexToUpdate === -1) {
                    this.cachedPosts = [createdPost, ...this.cachedPosts];
                    this.posts$.next(this.cachedPosts);
                    this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                }
            }),
        );
    }

    /**
     * creates a new answer post by invoking the answer post service
     * @param {AnswerPost} answerPost to be created
     * @return {Observable<AnswerPost>} created answer post
     */
    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(
            map((res: HttpResponse<AnswerPost>) => res.body!),
            tap((createdAnswerPost: AnswerPost) => {
                const indexOfCachedPost = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === answerPost.post?.id);
                if (indexOfCachedPost > -1) {
                    // Update the answers of the cached post, if the answer is not already included in the list of answers
                    const indexOfAnswer = this.cachedPosts[indexOfCachedPost].answers?.findIndex((answer) => answer.id === createdAnswerPost.id) ?? -1;
                    if (indexOfAnswer === -1) {
                        if (!this.cachedPosts[indexOfCachedPost].answers) {
                            // Need to create a new message object since Angular doesn't detect changes otherwise
                            this.cachedPosts[indexOfCachedPost] = { ...this.cachedPosts[indexOfCachedPost], answers: [], reactions: [] };
                        }
                        this.cachedPosts[indexOfCachedPost].answers!.push(createdAnswerPost);
                        this.posts$.next(this.cachedPosts);
                        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                    }
                }
            }),
        );
    }

    /**
     * updates a given posts by invoking the post service
     * @param {Post} post to be updated
     * @return {Observable<Post>} updated post
     */
    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(
            map((res: HttpResponse<Post>) => res.body!),
            tap((updatedPost: Post) => {
                const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === updatedPost.id);
                if (indexToUpdate > -1) {
                    updatedPost.answers = [...(this.cachedPosts[indexToUpdate].answers ?? [])];
                    this.cachedPosts[indexToUpdate] = updatedPost;
                    this.posts$.next(this.cachedPosts);
                    this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                }
            }),
        );
    }

    /**
     * updates a given answer posts by invoking the answer post service
     * @param {AnswerPost} answerPost to be updated
     * @return {Observable<AnswerPost>} updated answer post
     */
    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(
            map((res: HttpResponse<AnswerPost>) => res.body!),
            tap((updatedAnswerPost: AnswerPost) => {
                const indexOfCachedPost = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === answerPost.post?.id);
                if (indexOfCachedPost > -1) {
                    const indexOfAnswer = this.cachedPosts[indexOfCachedPost].answers?.findIndex((answer) => answer.id === updatedAnswerPost.id) ?? -1;
                    if (indexOfAnswer > -1) {
                        updatedAnswerPost.post = { ...this.cachedPosts[indexOfCachedPost], answers: [], reactions: [] };
                        updatedAnswerPost.authorRole = this.cachedPosts[indexOfCachedPost].answers![indexOfAnswer].authorRole;
                        this.cachedPosts[indexOfCachedPost].answers![indexOfAnswer] = updatedAnswerPost;
                        this.posts$.next(this.cachedPosts);
                        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                    }
                }
            }),
        );
    }

    /**
     * updates the display priority of a post to NONE, PINNED, ARCHIVED
     * @param {number} postId id of the post for which the displayPriority is changed
     * @param {DisplayPriority} displayPriority new displayPriority
     * @return {Observable<Post>} updated post
     */
    updatePostDisplayPriority(postId: number, displayPriority: DisplayPriority): Observable<Post> {
        return this.postService.updatePostDisplayPriority(this.courseId, postId, displayPriority).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * deletes a post by invoking the post service
     * @param {Post} post to be deleted
     */
    deletePost(post: Post): void {
        this.postService
            .delete(this.courseId, post)
            .pipe(
                tap(() => {
                    const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === post.id);
                    // Delete the cached post if it still exists (might be already deleted due to WebSocket message)
                    if (indexToUpdate > -1) {
                        this.cachedPosts.splice(indexToUpdate, 1);
                        this.posts$.next(this.cachedPosts);
                        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                    }
                }),
            )
            .subscribe();
    }

    /**
     * deletes an answer post by invoking the post service
     * @param {AnswerPost} answerPost to be deleted
     */
    deleteAnswerPost(answerPost: AnswerPost): void {
        this.answerPostService
            .delete(this.courseId, answerPost)
            .pipe(
                tap(() => {
                    const indexOfCachedPost = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === answerPost.post?.id);
                    if (indexOfCachedPost > -1) {
                        // Delete the answer if it still exists (might already be deleted due to WebSocket message)
                        const indexOfAnswer = this.cachedPosts[indexOfCachedPost].answers?.findIndex((answer) => answer.id === answerPost.id) ?? -1;
                        if (indexOfAnswer > -1) {
                            this.cachedPosts[indexOfCachedPost].answers?.splice(indexOfAnswer, 1);
                            this.posts$.next(this.cachedPosts);
                            this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                        }
                    }
                }),
            )
            .subscribe();
    }

    /**
     * creates a new reaction
     * @param {Reaction} reaction to be created
     * @return {Observable<Reaction>} created reaction
     */
    createReaction(reaction: Reaction): Observable<Reaction> {
        return this.reactionService.create(this.courseId, reaction).pipe(
            map((res: HttpResponse<Reaction>) => res.body!),
            tap((createdReaction: Reaction) => {
                const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === createdReaction.post?.id);
                if (indexToUpdate > -1) {
                    const cachedPost = this.cachedPosts[indexToUpdate];
                    const indexOfReaction = cachedPost.reactions?.findIndex((r) => r.id === createdReaction.id) ?? -1;
                    // Only add reaction if not already there (can happen due to WebSocket update)
                    if (indexOfReaction === -1) {
                        cachedPost.reactions = cachedPost.reactions ?? [];
                        cachedPost.reactions!.push(createdReaction);
                        // Need to create a new message object since Angular doesn't detect changes otherwise
                        this.cachedPosts[indexToUpdate] = { ...cachedPost };
                        this.posts$.next(this.cachedPosts);
                        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                    }
                }
            }),
        );
    }

    /**
     * deletes an existing reaction
     * @param {Reaction} reaction to be deleted
     */
    deleteReaction(reaction: Reaction): Observable<void> {
        return this.reactionService.delete(this.courseId, reaction).pipe(
            map((res: HttpResponse<void>) => res.body!),
            tap(() => {
                const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === reaction.post?.id);
                if (indexToUpdate > -1) {
                    // Delete the reaction from the post if it is not already deleted (can happen due to WebSocket message)
                    const cachedPost = this.cachedPosts[indexToUpdate];
                    const indexOfReaction = cachedPost.reactions?.findIndex((r) => r.id == reaction.id) ?? -1;
                    if (indexOfReaction > -1) {
                        cachedPost.reactions!.splice(indexOfReaction, 1);
                        // Need to create a new message object since Angular doesn't detect changes otherwise
                        this.cachedPosts[indexToUpdate] = { ...cachedPost };
                        this.posts$.next(this.cachedPosts);
                        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                    }
                }
            }),
        );
    }

    /**
     * determines if the current user is at least tutor in the current course
     * @return {boolean} tutor flag
     */
    metisUserIsAtLeastTutorInCourse(): boolean {
        return !!this.course.isAtLeastTutor;
    }

    /**
     * determines if the current user is at least instructor in the current course
     * @return boolean instructor flag
     */
    metisUserIsAtLeastInstructorInCourse(): boolean {
        return !!this.course.isAtLeastInstructor;
    }

    /**
     * determines if the current user is the author of a given posting
     * @param {Posting} posting for which the author is determined
     * @return {boolean} author flag
     */
    metisUserIsAuthorOfPosting(posting: Posting): boolean {
        if (posting?.author?.id && this.getUser()?.id) {
            return posting.author.id === this.getUser().id;
        } else {
            return false;
        }
    }
    /**
     * creates empty default post that is needed on initialization of a newly opened modal to edit or create a post
     * @param conversation optional conversation as default context
     * @param plagiarismCase optional plagiarism case as default context
     * @return {Post} created default object
     */
    createEmptyPostForContext(conversation?: Conversation, plagiarismCase?: PlagiarismCase): Post {
        const emptyPost: Post = new Post();
        if (conversation) {
            emptyPost.conversation = conversation;
        } else if (plagiarismCase) {
            emptyPost.plagiarismCase = { id: plagiarismCase.id } as PlagiarismCase;
        }
        return emptyPost;
    }

    /**
     * determines the router link components required for navigating to the detail view of the given post
     * @return {RouteComponents} array of router link components
     */
    getLinkForPost(): RouteComponents {
        return MetisService.getLinkForCoursePost(this.courseId);
    }

    /**
     * returns the router link required for navigating to the exercise referenced within a posting
     * @param {string} exerciseId ID of the exercise to be navigated to
     * @return {string} router link of the exercise
     */
    getLinkForExercise(exerciseId: string): string {
        return `/courses/${this.getCourse().id}/exercises/${exerciseId}`;
    }

    /**
     * returns the router link required for navigating to the lecture referenced within a posting
     * @param {string} lectureId ID of the lecture to be navigated to
     * @return {string} router link of the lecture
     */
    getLinkForLecture(lectureId: string): string {
        return `/courses/${this.getCourse().id}/lectures/${lectureId}`;
    }

    /**
     * returns the router link required for navigating to the exam
     * @param {string} examId ID of the exam to be navigated to
     * @return {string} router link of the exam
     */
    getLinkForExam(examId: string): string {
        return `/courses/${this.getCourse().id}/exams/${examId}`;
    }

    /**
     * returns the router link required for navigating to the channel subtype reference
     *
     * @param {ChannelDTO} channel
     * @return {string} router link of the channel subtype reference
     */
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

    /**
     * returns the router link required for navigating to the exercise referenced within a faq
     * @return {string} router link of the faq
     */
    getLinkForFaq(): string {
        return `/courses/${this.getCourse().id}/faq`;
    }

    /**
     * determines the routing params required for navigating to the detail view of the given post
     * @param {Post} post to be navigated to
     * @return {Params} required parameter key-value pair
     */
    getQueryParamsForPost(post: Post): Params {
        if (post.conversation) {
            return MetisService.getQueryParamsForCoursePost(post.id!);
        }
        return {};
    }

    /**
     * Creates an object to be used when a post context should be displayed and linked (for exercise and lecture)
     * @param {Post} post for which the context is displayed and linked
     * @return {ContextInformation} object containing the required router link components as well as the context display name
     */
    getContextInformation(post: Post): ContextInformation {
        let routerLinkComponents = undefined;
        let queryParams = undefined;
        let displayName = '';
        if (post.conversation) {
            displayName = getAsChannelDTO(post.conversation)?.name ?? '';
            routerLinkComponents = ['/courses', this.courseId, 'communication'];
            queryParams = { conversationId: post.conversation.id! };
        }
        return { routerLinkComponents, displayName, queryParams };
    }

    /**
     * Creates (and updates) the websocket channel for receiving messages in dedicated channels;
     * On message reception, subsequent actions for updating the dependent components are defined based on the MetisPostAction encapsulated in the MetisPostDTO (message payload);
     * Updating the components is achieved by manipulating the cached (i.e., currently visible posts) accordingly,
     * and emitting those as new value for the `posts` observable via the getFilteredPosts method
     * @param channel which the metis service should subscribe to
     */
    createWebsocketSubscription(channel: string): void {
        // if channel subscription does not change, do nothing
        if (this.subscriptionChannel === channel) {
            return;
        }
        // unsubscribe from existing channel subscription
        if (this.subscriptionChannel) {
            this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
            this.subscriptionChannel = undefined;
        }

        // create new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe(this.handleNewOrUpdatedMessage);
    }

    private handleNewOrUpdatedMessage = (postDTO: MetisPostDTO): void => {
        const postConvId = postDTO.post.conversation?.id;
        const postIsNotFromCurrentConversation = this.currentPostContextFilter.conversationId && postConvId !== this.currentPostContextFilter.conversationId;
        const postIsNotFromCurrentPlagiarismCase =
            this.currentPostContextFilter.plagiarismCaseId && postDTO.post.plagiarismCase?.id !== this.currentPostContextFilter.plagiarismCaseId;
        const postIsNotFromSelectedCourseWideChannels =
            this.currentPostContextFilter.courseWideChannelIds?.length !== undefined &&
            (!getAsChannelDTO(postDTO.post.conversation)?.isCourseWide ||
                (this.currentPostContextFilter.courseWideChannelIds.length > 0 && postConvId && !this.currentPostContextFilter.courseWideChannelIds.includes(postConvId)));

        if (postIsNotFromCurrentConversation || postIsNotFromSelectedCourseWideChannels || postIsNotFromCurrentPlagiarismCase) {
            return;
        }

        postDTO.post.creationDate = dayjs(postDTO.post.creationDate);
        postDTO.post.answers?.forEach((answer: AnswerPost) => {
            answer.creationDate = dayjs(answer.creationDate);
        });

        switch (postDTO.action) {
            case MetisPostAction.CREATE:
                const doesNotMatchOwnFilter = this.currentPostContextFilter.filterToOwn && postDTO.post.author?.id !== this.user.id;
                const doesNotMatchReactedFilter = this.currentPostContextFilter.filterToAnsweredOrReacted;
                const doesNotMatchSearchString =
                    this.currentPostContextFilter.searchText?.length &&
                    !postDTO.post.content?.toLowerCase().includes(this.currentPostContextFilter.searchText.toLowerCase().trim());

                if (doesNotMatchOwnFilter || doesNotMatchReactedFilter || doesNotMatchSearchString) {
                    break;
                }
                // we can add the received conversation message to the cached messages without violating the current context filter setting
                // prevent adding the same post multiple times
                const existingPostIndex = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                if (existingPostIndex === -1) {
                    if (this.currentPostContextFilter.sortingOrder === SortDirection.ASCENDING) {
                        this.cachedPosts.push(postDTO.post);
                    } else {
                        this.cachedPosts = [postDTO.post, ...this.cachedPosts];
                    }
                }

                if (this.currentPostContextFilter.conversationId && postDTO.post.author?.id !== this.user.id) {
                    this.conversationService.markAsRead(this.courseId, this.currentPostContextFilter.conversationId).subscribe();
                }

                this.addTags(postDTO.post.tags);
                break;
            case MetisPostAction.UPDATE:
                const indexToUpdate = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                if (indexToUpdate > -1) {
                    // WebSocket does not currently update the author and authorRole of posts correctly, so this is implemented as a workaround
                    postDTO.post.authorRole = this.cachedPosts[indexToUpdate].authorRole;
                    postDTO.post.answers?.forEach((answer: AnswerPost) => {
                        const cachedAnswer = this.cachedPosts[indexToUpdate].answers?.find((a) => a.id === answer.id);
                        if (cachedAnswer) {
                            answer.authorRole = cachedAnswer.authorRole;
                        }
                    });
                    this.cachedPosts[indexToUpdate] = postDTO.post;
                }
                this.addTags(postDTO.post.tags);
                break;
            case MetisPostAction.DELETE:
                const indexToDelete = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                if (indexToDelete > -1) {
                    this.cachedPosts.splice(indexToDelete, 1);
                }
                break;
            default:
                break;
        }
        // emit updated version of cachedPosts to subscribing components...
        if (PageType.OVERVIEW === this.pageType) {
            const oldPage = this.currentPostContextFilter.page;
            const oldPageSize = this.currentPostContextFilter.pageSize;
            this.currentPostContextFilter.pageSize = oldPageSize! * (oldPage! + 1);
            this.currentPostContextFilter.page = 0;
            // ...by invoking the getFilteredPosts method with forceUpdate set to true iff receiving a new Q&A post, i.e. fetching posts from server only in this case
            this.getFilteredPosts(this.currentPostContextFilter, !postConvId, this.currentConversation);
            this.currentPostContextFilter.pageSize = oldPageSize;
            this.currentPostContextFilter.page = oldPage;
        } else {
            // ...by invoking the getFilteredPosts method with forceUpdate set to false, i.e. without fetching posts from server
            this.getFilteredPosts(this.currentPostContextFilter, false);
        }
    };

    /**
     * Determines the channel to be used for websocket communication based on the current post context filter,
     * i.e., when being on a lecture page, the context is a certain lectureId (e.g., 1), the channel is set to '/topic/metis/lectures/1';
     * By calling the createWebsocketSubscription method with this channel as parameter, the metis service also subscribes to that messages in this channel
     */
    private createSubscriptionFromPostContextFilter(): void {
        if (this.currentPostContextFilter.plagiarismCaseId) {
            const channel = MetisWebsocketChannelPrefix + 'plagiarismCase/' + this.currentPostContextFilter.plagiarismCaseId;
            this.createWebsocketSubscription(channel);
        } else {
            // No need for extra subscription since messaging topics are covered by other services
            if (this.subscriptionChannel) {
                this.jhiWebsocketService.unsubscribe(this.subscriptionChannel);
                this.subscriptionChannel = undefined;
            }
            return;
        }
    }

    /**
     * Helper method to add tags to currently stored course tags
     */
    private addTags(tags: string[] | undefined) {
        if (tags && tags.length > 0) {
            const updatedTags = Array.from(new Set([...this.tags$.getValue(), ...tags]));
            this.tags$.next(updatedTags);
        }
    }

    private hasDifferentContexts(other: PostContextFilter): boolean {
        this.currentPostContextFilter.courseWideChannelIds?.sort((a, b) => a - b);
        other.courseWideChannelIds?.sort((a, b) => a - b);

        return this.currentPostContextFilter.courseWideChannelIds?.toString() !== other.courseWideChannelIds?.toString();
    }
}
