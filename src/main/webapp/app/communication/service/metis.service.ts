import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable, OnDestroy, inject } from '@angular/core';
import { Params } from '@angular/router';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ForwardedMessageService } from 'app/communication/service/forwarded-message.service';
import {
    ContextInformation,
    DisplayPriority,
    MetisPostAction,
    MetisWebsocketChannelPrefix,
    PageType,
    PostContextFilter,
    PostSortCriterion,
    RouteComponents,
    SortDirection,
} from 'app/communication/metis.util';
import { PostService } from 'app/communication/service/post.service';
import { ReactionService } from 'app/communication/service/reaction.service';
import { SavedPostService } from 'app/communication/service/saved-post.service';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Conversation, ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { getAsGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { getAsOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { Faq } from 'app/communication/shared/entities/faq.model';
import { ForwardedMessage, ForwardedMessagesGroupDTO } from 'app/communication/shared/entities/forwarded-message.model';
import { MetisPostDTO } from 'app/communication/shared/entities/metis-post-dto.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { Posting, PostingType, SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { Reaction } from 'app/communication/shared/entities/reaction.model';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { PlagiarismCase } from 'app/plagiarism/shared/entities/PlagiarismCase';
import { WebsocketService } from 'app/shared/service/websocket.service';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { BehaviorSubject, Observable, ReplaySubject, Subscription, catchError, forkJoin, map, of, switchMap, tap, throwError } from 'rxjs';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';

@Injectable()
export class MetisService implements OnDestroy {
    private postService = inject(PostService);
    private answerPostService = inject(AnswerPostService);
    private reactionService = inject(ReactionService);
    private accountService = inject(AccountService);
    private websocketService = inject(WebsocketService);
    private conversationService = inject(ConversationService);
    private forwardedMessageService = inject(ForwardedMessageService);
    private savedPostService = inject(SavedPostService);
    private metisConversationService = inject(MetisConversationService);
    private http = inject(HttpClient);
    private posts$: ReplaySubject<Post[]> = new ReplaySubject<Post[]>(1);
    private totalNumberOfPosts$: ReplaySubject<number> = new ReplaySubject<number>(1);
    private pinnedPosts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);

    private currentPostContextFilter: PostContextFilter = {};
    private currentConversation?: ConversationDTO = undefined;
    private user: User;
    private pageType: PageType;
    private courseId: number;
    private cachedPosts: Post[] = [];
    private cachedTotalNumberOfPosts: number;
    private subscriptionChannel?: string;
    private courseWideTopicSubscription: Subscription;
    private activeConversationSubscription: Subscription;

    private course: Course;
    // Expose FAQs as observable so consumers react once async loading finishes (setupMetis fetches from REST)
    private faqs$: BehaviorSubject<Faq[]> = new BehaviorSubject<Faq[]>([]);

    constructor() {
        this.accountService.identity().then((user: User) => {
            this.user = user!;

            const conversationTopic = `/topic/user/${this.user.id}/notifications/conversations`;
            this.websocketService.subscribe(conversationTopic);
            this.activeConversationSubscription = this.websocketService.receive(conversationTopic).subscribe((postDTO: MetisPostDTO) => {
                this.handleNewOrUpdatedMessage(postDTO);
            });
        });
    }

    get posts(): Observable<Post[]> {
        return this.posts$.asObservable();
    }

    get totalNumberOfPosts(): Observable<number> {
        return this.totalNumberOfPosts$.asObservable();
    }

    getPinnedPosts(): Observable<Post[]> {
        return this.pinnedPosts$.asObservable();
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
            this.websocketService.unsubscribe(this.subscriptionChannel);
        }
        if (this.courseWideTopicSubscription) {
            this.courseWideTopicSubscription.unsubscribe();
        }
        if (this.activeConversationSubscription) {
            this.activeConversationSubscription.unsubscribe();
        }
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

    getFaqs(): Observable<Faq[]> {
        return this.faqs$.asObservable();
    }

    setFaqs(faqs: Faq[]): void {
        this.faqs$.next(faqs);
    }

    /**
     * set course property before using metis service
     * @param {Course} course in which the metis service is used
     */
    setCourse(course: Course | undefined): void {
        if (course && (this.courseId === undefined || this.courseId !== course.id)) {
            this.courseId = course.id!;
            this.course = course;

            if (this.courseWideTopicSubscription) {
                this.courseWideTopicSubscription.unsubscribe();
            }

            const coursewideTopic = `/topic/metis/courses/${this.courseId}`;
            this.websocketService.subscribe(coursewideTopic);
            this.courseWideTopicSubscription = this.websocketService.receive(coursewideTopic).subscribe((postDTO: MetisPostDTO) => {
                this.handleNewOrUpdatedMessage(postDTO);
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
            postContextFilter?.conversationIds !== this.currentPostContextFilter?.conversationIds ||
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
            // if we do not require force update, e.g. because only the post title or content changed,
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
                            this.cachedPosts[indexOfCachedPost] = Object.assign({}, this.cachedPosts[indexOfCachedPost], { answers: [], reactions: [] });
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
     * @param post to be updated
     * @return updated post
     */
    updatePost(post: Post): Observable<Post> {
        if (post.id) {
            const updateIndex = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === post.id);
            const foundCachedPost = updateIndex !== -1;
            if (foundCachedPost) {
                // We update the date immediately so that the client is aware about the post being edited without having to wait for the update call to finish
                this.cachedPosts[updateIndex].updatedDate = dayjs();
                this.cachedPosts[updateIndex].content = post.content;
                this.posts$.next(this.cachedPosts);
            }
        }

        return this.postService.update(this.courseId, post).pipe(
            map((res: HttpResponse<Post>) => res.body!),
            tap((updatedPost: Post) => {
                const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === updatedPost.id);
                if (indexToUpdate > -1) {
                    updatedPost.answers = [...(this.cachedPosts[indexToUpdate].answers ?? [])];
                    updatedPost.authorRole = this.cachedPosts[indexToUpdate].authorRole;
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
                        updatedAnswerPost.post = Object.assign({}, this.cachedPosts[indexOfCachedPost], { answers: [], reactions: [] });
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
     * @param postId id of the post for which the displayPriority is changed
     * @param displayPriority new displayPriority
     * @return updated post
     */
    updatePostDisplayPriority(postId: number, displayPriority: DisplayPriority): Observable<Post> {
        return this.postService.updatePostDisplayPriority(this.courseId, postId, displayPriority).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * Fetches all pinned posts for the given conversation.
     * Posts are sorted by creation date in descending order and are not paginated.
     * Updates the internal pinnedPosts$ observable with the result.
     *
     * @param conversationId The ID of the conversation to fetch pinned posts from
     * @returns An observable of the fetched pinned posts
     */
    public fetchAllPinnedPosts(conversationId: number): Observable<Post[]> {
        const pinnedFilter: PostContextFilter = {
            courseId: this.courseId,
            conversationIds: [conversationId],
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            sortingOrder: SortDirection.DESCENDING,
            pinnedOnly: true,
            pagingEnabled: false,
        };

        return this.postService.getPosts(this.courseId, pinnedFilter).pipe(
            map((res: HttpResponse<Post[]>) => res.body ?? []),
            tap((pinnedPosts: Post[]) => {
                this.pinnedPosts$.next(pinnedPosts);
            }),
            catchError(() => {
                this.pinnedPosts$.next([]);
                return of([]);
            }),
        );
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
                        this.cachedPosts[indexToUpdate] = Object.assign({}, cachedPost);
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
                        this.cachedPosts[indexToUpdate] = Object.assign({}, cachedPost);
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
     * returns the router link required for navigating to the dashboard
     * @return {string} router link of the dashboard
     */
    getLinkForGeneral(): string {
        return `/courses/${this.getCourse().id}/dashboard`;
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
            if (channel?.subType === ChannelSubType.GENERAL) {
                return this.getLinkForGeneral();
            }
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
            if (getAsChannelDTO(post.conversation)) {
                displayName = getAsChannelDTO(post.conversation)?.name ?? '';
            } else if (getAsOneToOneChatDTO(post.conversation)) {
                displayName = 'Direct Message';
            } else if (getAsGroupChatDTO(post.conversation)) {
                displayName = 'Group Message';
            }
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
            this.websocketService.unsubscribe(this.subscriptionChannel);
            this.subscriptionChannel = undefined;
        }

        // create new subscription
        this.subscriptionChannel = channel;
        this.websocketService.subscribe(this.subscriptionChannel);
        this.websocketService.receive(this.subscriptionChannel).subscribe(this.handleNewOrUpdatedMessage);
    }

    public savePost(post: Posting) {
        this.setIsSavedAndStatusOfPost(post, true, post.savedPostStatus as SavedPostStatus);
        this.savedPostService.savePost(post).subscribe({
            next: () => {},
        });
        this.posts$.next(this.cachedPosts);
    }

    public removeSavedPost(post: Posting) {
        this.setIsSavedAndStatusOfPost(post, false, post.savedPostStatus as SavedPostStatus);
        this.savedPostService.removeSavedPost(post).subscribe({
            next: () => {},
        });
        this.posts$.next(this.cachedPosts);
    }

    public changeSavedPostStatus(post: Posting, status: SavedPostStatus) {
        this.setIsSavedAndStatusOfPost(post, post.isSaved, status);
        this.savedPostService.changeSavedPostStatus(post, status).subscribe({
            next: () => {},
        });
        this.posts$.next(this.cachedPosts);
    }

    public resetCachedPosts() {
        this.cachedPosts = [];
        this.posts$.next(this.cachedPosts);
        this.cachedTotalNumberOfPosts = 0;
        this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
    }

    private setIsSavedAndStatusOfPost(post: Posting, isSaved: undefined | boolean, status: undefined | SavedPostStatus) {
        if (post instanceof AnswerPost) {
            const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === post.post!.id);
            const indexOfAnswer = this.cachedPosts[indexToUpdate].answers?.findIndex((answer) => answer.id === post.id) ?? -1;
            const postCopy = cloneDeep(this.cachedPosts[indexToUpdate].answers![indexOfAnswer]);
            postCopy.isSaved = isSaved;
            postCopy.savedPostStatus = status;
            this.cachedPosts[indexToUpdate].answers![indexOfAnswer] = postCopy;
        } else {
            const indexToUpdate = this.cachedPosts.findIndex((cachedPost) => cachedPost.id === post.id);
            const postCopy = cloneDeep(this.cachedPosts[indexToUpdate]);
            postCopy.isSaved = isSaved;
            postCopy.savedPostStatus = status;
            this.cachedPosts[indexToUpdate] = postCopy;
        }
    }

    private handleNewOrUpdatedMessage = (postDTO: MetisPostDTO): void => {
        const postConvId = postDTO.post.conversation?.id;
        const isValidPostContext = !!postConvId && !!this.currentPostContextFilter.conversationIds && this.currentPostContextFilter.conversationIds.length > 0;
        const postIsFromCurrentConversation = isValidPostContext && this.currentPostContextFilter.conversationIds?.includes(postConvId);
        const postIsPrivate = !!this.currentPostContextFilter.filterToCourseWide && !getAsChannelDTO(postDTO.post.conversation)?.isCourseWide;
        const postIsNotFromCurrentPlagiarismCase =
            this.currentPostContextFilter.plagiarismCaseId && postDTO.post.plagiarismCase?.id !== this.currentPostContextFilter.plagiarismCaseId;

        if (postDTO.action === MetisPostAction.CREATE && postDTO.post.conversation?.id !== this.currentConversation?.id && postDTO.post.author?.id !== this.user.id) {
            this.metisConversationService.handleNewMessage(postConvId, postDTO.post.creationDate);
        }

        if (!isValidPostContext || !postIsFromCurrentConversation || postIsNotFromCurrentPlagiarismCase || postIsPrivate) {
            return;
        }

        postDTO.post.creationDate = dayjs(postDTO.post.creationDate);
        postDTO.post.answers?.forEach((answer: AnswerPost) => {
            answer.creationDate = dayjs(answer.creationDate);
        });

        switch (postDTO.action) {
            case MetisPostAction.CREATE:
                const isAuthorFilterActive = this.currentPostContextFilter.authorIds && this.currentPostContextFilter.authorIds?.length > 0;
                const doesNotMatchAuthorFilter = isAuthorFilterActive && postDTO.post.author?.id && !this.currentPostContextFilter.authorIds?.includes(postDTO.post.author?.id);
                const doesNotMatchReactedFilter = this.currentPostContextFilter.filterToAnsweredOrReacted;
                const doesNotMatchSearchString =
                    this.currentPostContextFilter.searchText?.length &&
                    !postDTO.post.content?.toLowerCase().includes(this.currentPostContextFilter.searchText.toLowerCase().trim());

                if (doesNotMatchAuthorFilter || doesNotMatchReactedFilter || doesNotMatchSearchString) {
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

                if (this.currentPostContextFilter.conversationIds && this.currentPostContextFilter.conversationIds.length == 1 && postDTO.post.author?.id !== this.user.id) {
                    setTimeout(() => {
                        // We add a small timeout to avoid concurrency issues
                        this.conversationService.markAsRead(this.courseId, this.currentPostContextFilter!.conversationIds![0]).subscribe();
                    }, 1000);
                }

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

                        // The updates only set the post.id property of answers, so we set the author and conversation properties manually
                        // to ensure the same answer.post structure as from the get-messages call.
                        answer.post = {
                            id: postDTO.post.id,
                            author: postDTO.post.author,
                            conversation: postDTO.post.conversation,
                        };
                    });
                    this.cachedPosts[indexToUpdate] = postDTO.post;
                }
                if (postDTO.post.displayPriority === DisplayPriority.PINNED) {
                    const currentPinnedPosts = this.pinnedPosts$.getValue();
                    const indexPinned = currentPinnedPosts.findIndex((pinnedPost) => pinnedPost.id === postDTO.post.id);
                    if (indexPinned > -1) {
                        // Post is already pinned → update its reference in the pinned list
                        currentPinnedPosts[indexPinned] = postDTO.post;
                        this.pinnedPosts$.next([...currentPinnedPosts]);
                    } else {
                        // Post just got pinned → prepend to pinned list
                        this.pinnedPosts$.next([postDTO.post, ...currentPinnedPosts]);
                    }
                } else {
                    // If post is no longer pinned, remove it from the pinned list
                    this.removeFromPinnedPosts(postDTO.post.id!);
                }
                break;
            case MetisPostAction.DELETE:
                const indexToDelete = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                if (indexToDelete > -1) {
                    this.cachedPosts.splice(indexToDelete, 1);
                }
                const currentPinnedPosts = this.pinnedPosts$.getValue();
                const isPinned = currentPinnedPosts.some((pinnedPost) => pinnedPost.id === postDTO.post.id);
                if (isPinned) {
                    // If a deleted post was pinned, remove it from the pinned list
                    const updatedPinnedPosts = currentPinnedPosts.filter((pinnedPost) => pinnedPost.id !== postDTO.post.id);
                    this.pinnedPosts$.next(updatedPinnedPosts);
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
                this.websocketService.unsubscribe(this.subscriptionChannel);
                this.subscriptionChannel = undefined;
            }
            return;
        }
    }

    /**
     * Retrieves forwarded messages for a given set of IDs and message type.
     *
     * @param postingIds - An array of numeric IDs for which forwarded messages should be retrieved.
     * @param type - The type of messages to retrieve ('post' or 'answer').
     * @returns An observable containing a list of objects where each object includes an ID and its corresponding messages (as DTOs), wrapped in an HttpResponse, or undefined if the IDs are invalid.
     */
    getForwardedMessagesByIds(postingIds: number[], type: PostingType): Observable<HttpResponse<ForwardedMessagesGroupDTO[]>> | undefined {
        if (postingIds && postingIds.length > 0) {
            return this.forwardedMessageService.getForwardedMessages(postingIds, type);
        } else {
            return undefined;
        }
    }

    /**
     * Retrieves the source posts for a given set of post IDs.
     *
     * @param postIds - An array of numeric post IDs to retrieve source posts for.
     * @returns An observable containing the source posts or undefined if the IDs are invalid or not existent.
     */
    getSourcePostsByIds(postIds: number[]): Observable<Post[] | undefined> {
        if (postIds) {
            return this.postService.getSourcePostsByIds(this.courseId, postIds).pipe(
                catchError((error) => {
                    if (error.status === 404) {
                        return of(undefined);
                    }
                    return throwError(() => error);
                }),
            );
        } else {
            return of(undefined);
        }
    }

    /**
     * Retrieves the source answer posts for a given set of answer post IDs.
     *
     * @param answerPostIds - An array of numeric answer post IDs to retrieve source answer posts for.
     * @returns An observable containing the source answer posts or undefined if the IDs are invalid or not existent.
     */
    getSourceAnswerPostsByIds(answerPostIds: number[]): Observable<AnswerPost[] | undefined> {
        if (answerPostIds) {
            return this.answerPostService.getSourceAnswerPostsByIds(this.courseId, answerPostIds).pipe(
                catchError((error) => {
                    if (error.status === 404) {
                        return of(undefined);
                    }
                    return throwError(() => error);
                }),
            );
        } else {
            return of(undefined);
        }
    }

    /**
     * Creates forwarded messages by associating original posts with a target conversation.
     *
     * @param originalPosts - An array of original posts to be forwarded.
     * @param targetConversation - The target conversation where the posts will be forwarded.
     * @param isAnswer - A boolean indicating if the forwarded posts are answers.
     * @param newContent - Optional new content for the forwarded posts.
     * @returns An observable containing an array of created ForwardedMessage objects.
     *
     * @throws Error if the course ID is not set.
     */
    createForwardedMessages(originalPosts: Posting[], targetConversation: Conversation, isAnswer: boolean, newContent?: string): Observable<ForwardedMessage[]> {
        if (!this.courseId) {
            return throwError(() => new Error('Course ID is not set. Ensure that setCourse() is called before forwarding posts.'));
        }

        // Create a new post object that will serve as the container for the forwarded messages
        const newPost: Post = {
            content: newContent || '',
            conversation: targetConversation,
            hasForwardedMessages: true,
        };

        // Determine whether the forwarded items are posts or answers
        let sourceType = PostingType.POST;
        if (isAnswer) {
            sourceType = PostingType.ANSWER;
        }

        // Create the new post on the server
        return this.postService.create(this.courseId, newPost).pipe(
            switchMap((createdPost: HttpResponse<Post>) => {
                const createdPostBody = createdPost.body!;

                // Map original posts to ForwardedMessage instances referencing the newly created post
                const forwardedMessages: ForwardedMessage[] = originalPosts.map(
                    (post) => new ForwardedMessage(undefined, post.id, sourceType, { id: createdPostBody.id } as Post, undefined, newContent || ''),
                );

                // Send a creation request for each ForwardedMessage
                const createForwardedMessageObservables = forwardedMessages.map((message) =>
                    this.forwardedMessageService.createForwardedMessage(message).pipe(map((res: HttpResponse<ForwardedMessage>) => res.body!)),
                );

                return forkJoin(createForwardedMessageObservables).pipe(
                    tap((createdForwardedMessages: ForwardedMessage[]) => {
                        // If the target is the currently active conversation, update the local cache
                        if (targetConversation.id === this.currentConversation?.id) {
                            const existingPostIndex = this.cachedPosts.findIndex((post) => post.id === createdPostBody.id);
                            if (existingPostIndex === -1) {
                                this.cachedPosts = [createdPostBody, ...this.cachedPosts];
                            }

                            // Mark posts as having forwarded messages
                            createdForwardedMessages.forEach((fm) => {
                                const postIndex = this.cachedPosts.findIndex((post) => post.id === fm.destinationPost?.id);
                                if (postIndex > -1) {
                                    const post = this.cachedPosts[postIndex];
                                    this.cachedPosts[postIndex] = Object.assign({}, post, { hasForwardedMessages: true });
                                }
                            });

                            // Emit updated posts and post count
                            this.posts$.next(this.cachedPosts);
                            this.cachedTotalNumberOfPosts += 1;
                            this.totalNumberOfPosts$.next(this.cachedTotalNumberOfPosts);
                        }
                    }),
                    catchError((error) => {
                        return throwError(() => error);
                    }),
                );
            }),
            catchError((error) => {
                return throwError(() => error);
            }),
        );
    }

    private hasDifferentContexts(other: PostContextFilter): boolean {
        this.currentPostContextFilter.conversationIds?.sort((a, b) => a - b);
        other.conversationIds?.sort((a, b) => a - b);

        return this.currentPostContextFilter.conversationIds?.toString() !== other.conversationIds?.toString();
    }

    /**
     * Removes a post from the pinnedPosts$ if it exists.
     * @param postId The ID of the post to remove.
     */
    private removeFromPinnedPosts(postId: number): void {
        const currentPinnedPosts = this.pinnedPosts$.getValue();
        const isPinned = currentPinnedPosts.some((pinnedPost) => pinnedPost.id === postId);
        if (isPinned) {
            const updatedPinnedPosts = currentPinnedPosts.filter((pinnedPost) => pinnedPost.id !== postId);
            this.pinnedPosts$.next(updatedPinnedPosts);
        }
    }

    enable(courseId: number, withMessaging: boolean): Observable<void> {
        const httpParams = new HttpParams().set('withMessaging', withMessaging);
        return this.http.put<void>('api/communication/courses/' + courseId + '/enable', undefined, { params: httpParams });
    }
}
