import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { BehaviorSubject, map, Observable, ReplaySubject } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';
import { Injectable, OnDestroy } from '@angular/core';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import {
    ContextInformation,
    CourseWideContext,
    DisplayPriority,
    MetisPostAction,
    MetisWebsocketChannelPrefix,
    PageType,
    PostContextFilter,
    RouteComponents,
} from 'app/shared/metis/metis.util';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Params } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { MetisPostDTO } from 'app/entities/metis/metis-post-dto.model';
import dayjs from 'dayjs/esm';
import { PlagiarismCase } from 'app/exercises/shared/plagiarism/types/PlagiarismCase';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';

@Injectable()
export class MetisService implements OnDestroy {
    private posts$: ReplaySubject<Post[]> = new ReplaySubject<Post[]>(1);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private totalItems$: ReplaySubject<number> = new ReplaySubject<number>(1);

    private currentPostContextFilter: PostContextFilter = {};
    private user: User;
    private pageType: PageType;
    private course: Course;
    private courseId: number;
    private cachedPosts: Post[];
    private cachedTotalItems: number;
    private subscriptionChannel?: string;

    private forceUpdate: boolean;

    constructor(
        protected postService: PostService,
        protected answerPostService: AnswerPostService,
        protected reactionService: ReactionService,
        protected accountService: AccountService,
        protected exerciseService: ExerciseService,
        private translateService: TranslateService,
        private jhiWebsocketService: JhiWebsocketService,
    ) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
    }

    get posts(): Observable<Post[]> {
        return this.posts$.asObservable();
    }

    get tags(): Observable<string[]> {
        return this.tags$.asObservable();
    }

    get totalItems(): Observable<number> {
        return this.totalItems$.asObservable();
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
    setCourse(course: Course): void {
        if (this.courseId === undefined || this.courseId !== course.id) {
            this.courseId = course.id!;
            this.course = course;
            this.updateCoursePostTags();
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
     * fetches all post tags used in the current course, informs all subscribing components
     */
    updateCoursePostTags(): void {
        this.postService
            .getAllPostTagsByCourseId(this.courseId)
            .pipe(map((res: HttpResponse<string[]>) => res.body!.filter((tag) => !!tag)))
            .subscribe((tags: string[]) => {
                this.tags$.next(tags);
            });
    }

    /**
     * fetches all posts for a course, optionally fetching posts only for a certain context, i.e. a lecture, exercise or specified course-wide-context,
     * informs all components that subscribed on posts by sending the newly fetched posts
     * @param {PostContextFilter} postContextFilter criteria to filter course posts with (lecture, exercise, course-wide context)
     * @param {boolean} forceUpdate if true, forces a re-fetch even if filter property did not change
     */
    getFilteredPosts(postContextFilter: PostContextFilter, forceUpdate = true): void {
        // store value for promise
        this.forceUpdate = forceUpdate;

        // check if the post context did change
        if (
            forceUpdate ||
            postContextFilter?.courseId !== this.currentPostContextFilter?.courseId ||
            postContextFilter?.conversationId !== this.currentPostContextFilter?.conversationId ||
            postContextFilter?.courseWideContext !== this.currentPostContextFilter?.courseWideContext ||
            postContextFilter?.lectureId !== this.currentPostContextFilter?.lectureId ||
            postContextFilter?.exerciseId !== this.currentPostContextFilter?.exerciseId ||
            postContextFilter?.plagiarismCaseId !== this.currentPostContextFilter?.plagiarismCaseId ||
            postContextFilter?.page !== this.currentPostContextFilter?.page
        ) {
            this.currentPostContextFilter = postContextFilter;
            this.postService.getPosts(this.courseId, postContextFilter).subscribe((res) => {
                if (!forceUpdate && PageType.OVERVIEW === this.pageType) {
                    // if infinite scroll enabled, add fetched posts to the end of cachedPosts
                    this.cachedPosts.push.apply(this.cachedPosts, res.body!);
                } else {
                    // if the context changed, we need to fetch posts and dismiss cached posts
                    this.cachedPosts = res.body!;
                }
                this.cachedTotalItems = Number(res.headers.get('X-Total-Count'));
                this.posts$.next(this.cachedPosts);
                this.totalItems$.next(this.cachedTotalItems);
                this.createSubscriptionFromPostContextFilter();
            });
        } else {
            // if we do not require force update, e.g. because only the post title, tag or content changed,
            // we can emit the previously cached posts
            this.posts$.next(this.cachedPosts);
            this.totalItems$.next(this.cachedTotalItems);
        }
    }

    /**
     * creates a new post by invoking the post service
     * @param {Post} post to be created
     * @return {Observable<Post>} created post
     */
    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * creates a new answer post by invoking the answer post service
     * @param {AnswerPost} answerPost to be created
     * @return {Observable<AnswerPost>} created answer post
     */
    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * updates a given posts by invoking the post service
     * @param {Post} post to be updated
     * @return {Observable<Post>} updated post
     */
    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * updates a given answer posts by invoking the answer post service
     * @param {AnswerPost} answerPost to be updated
     * @return {Observable<AnswerPost>} updated answer post
     */
    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(map((res: HttpResponse<Post>) => res.body!));
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
        this.postService.delete(this.courseId, post).subscribe();
    }

    /**
     * deletes an answer post by invoking the post service
     * @param {AnswerPost} answerPost to be deleted
     */
    deleteAnswerPost(answerPost: AnswerPost): void {
        this.answerPostService.delete(this.courseId, answerPost).subscribe();
    }

    /**
     * creates a new reaction
     * @param {Reaction} reaction to be created
     * @return {Observable<Reaction>} created reaction
     */
    createReaction(reaction: Reaction): Observable<Reaction> {
        return this.reactionService.create(this.courseId, reaction).pipe(map((res: HttpResponse<Post>) => res.body!));
    }

    /**
     * deletes an existing reaction
     * @param {Reaction} reaction to be deleted
     */
    deleteReaction(reaction: Reaction): Observable<void> {
        return this.reactionService.delete(this.courseId, reaction).pipe(map((res: HttpResponse<void>) => res.body!));
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
        return this.getUser() ? posting?.author!.id === this.getUser().id : false;
    }

    /**
     * creates empty default post that is needed on initialization of a newly opened modal to edit or create a post
     * @param {CourseWideContext | undefined} courseWideContext optional course-wide context as default context
     * @param {Exercise | undefined} exercise optional exercise as default context
     * @param {Lecture | undefined} lecture optional lecture as default context
     * @return {Post} created default object
     */
    createEmptyPostForContext(courseWideContext?: CourseWideContext, exercise?: Exercise, lecture?: Lecture, plagiarismCase?: PlagiarismCase, conversation?: Conversation): Post {
        const emptyPost: Post = new Post();
        if (courseWideContext) {
            emptyPost.courseWideContext = courseWideContext;
            emptyPost.course = this.course;
        } else if (exercise) {
            const exercisePost = ExerciseService.convertExerciseFromClient(exercise);
            emptyPost.exercise = { id: exercisePost.id, title: exercisePost.title, type: exercisePost.type } as Exercise;
        } else if (lecture) {
            emptyPost.lecture = { id: lecture.id, title: lecture.title } as Lecture;
        } else if (plagiarismCase) {
            emptyPost.plagiarismCase = { id: plagiarismCase.id } as PlagiarismCase;
        } else if (conversation) {
            emptyPost.conversation = conversation;
        } else {
            // set default
            emptyPost.courseWideContext = CourseWideContext.TECH_SUPPORT as CourseWideContext;
        }
        return emptyPost;
    }

    /**
     * determines if the post is resolved by searching for resolving answer posts
     * @param {Post} post of which the state is determined
     * @return {boolean} flag that indicates if the post is resolved
     */
    isPostResolved(post: Post): boolean {
        if (post.answers && post.answers.length > 0) {
            return !!post.answers.find((answerPosts: AnswerPost) => answerPosts.resolvesPost);
        } else {
            return false;
        }
    }

    /**
     * determines the router link components required for navigating to the detail view of the given post
     * @param {Post} post to be navigated to
     * @return {RouteComponents} array of router link components
     */
    getLinkForPost(post?: Post): RouteComponents {
        if (post?.lecture) {
            return MetisService.getLinkForLecturePost(this.courseId, post.lecture.id!);
        }
        if (post?.exercise) {
            return MetisService.getLinkForExercisePost(this.courseId, post.exercise.id!);
        }
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
     * determines the routing params required for navigating to the detail view of the given post
     * @param {Post} post to be navigated to
     * @return {Params} required parameter key-value pair
     */
    getQueryParamsForPost(post: Post): Params {
        if (post.courseWideContext) {
            return MetisService.getQueryParamsForCoursePost(post.id!);
        } else {
            return MetisService.getQueryParamsForLectureOrExercisePost(post.id!);
        }
    }

    /**
     * Creates an object to be used when a post context should be displayed and linked (for exercise and lecture)
     * @param {Post} post for which the context is displayed and linked
     * @return {ContextInformation} object containing the required router link components as well as the context display name
     */
    getContextInformation(post: Post): ContextInformation {
        let routerLinkComponents = undefined;
        let displayName;
        if (post.exercise) {
            displayName = post.exercise.title!;
            routerLinkComponents = ['/courses', this.courseId, 'exercises', post.exercise.id!];
        } else if (post.lecture) {
            displayName = post.lecture.title!;
            routerLinkComponents = ['/courses', this.courseId, 'lectures', post.lecture.id!];
        } else {
            // course-wide topics are not linked, only displayName is set
            displayName = this.translateService.instant('artemisApp.metis.overview.' + post.courseWideContext);
        }
        return { routerLinkComponents, displayName };
    }

    /**
     * Invokes the post service to get a top-k-list of course posts with high similarity scores when compared with a certain strategy
     * @param {Post} tempPost that is currently created and compared against existing course posts on updates in the form group
     * @return {Observable<Post[]>} array of similar posts that were found in the course
     */
    getSimilarPosts(tempPost: Post): Observable<Post[]> {
        return this.postService.computeSimilarityScoresWithCoursePosts(tempPost, this.courseId).pipe(map((res: HttpResponse<Post[]>) => res.body!));
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
        }
        // create new subscription
        this.subscriptionChannel = channel;
        this.jhiWebsocketService.subscribe(this.subscriptionChannel);
        this.jhiWebsocketService.receive(this.subscriptionChannel).subscribe((postDTO: MetisPostDTO) => {
            postDTO.post.creationDate = dayjs(postDTO.post.creationDate);
            postDTO.post.answers?.forEach((answer: AnswerPost) => {
                answer.creationDate = dayjs(answer.creationDate);
            });
            switch (postDTO.action) {
                case MetisPostAction.CREATE:
                    // determine if either the current post context filter is not set to a specific course-wide topic
                    // or the sent post has a different context,
                    // or the sent post has a course-wide context which matches the current filter
                    if (
                        !this.currentPostContextFilter.courseWideContext ||
                        !postDTO.post.courseWideContext ||
                        this.currentPostContextFilter.courseWideContext === postDTO.post.courseWideContext
                    ) {
                        // we can add the sent post to the cached posts without violating the current context filter setting
                        this.cachedPosts.push(postDTO.post);
                    }
                    this.addTags(postDTO.post.tags);
                    break;
                case MetisPostAction.UPDATE:
                    const indexToUpdate = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                    if (indexToUpdate > -1) {
                        this.cachedPosts[indexToUpdate] = postDTO.post;
                    } else {
                        console.error(`Post with id ${postDTO.post.id} could not be updated`);
                    }
                    this.addTags(postDTO.post.tags);
                    break;
                case MetisPostAction.DELETE:
                    const indexToDelete = this.cachedPosts.findIndex((post) => post.id === postDTO.post.id);
                    if (indexToDelete > -1) {
                        this.cachedPosts.splice(indexToDelete, 1);
                    } else {
                        console.error(`Post with id ${postDTO.post.id} could not be deleted`);
                    }
                    break;
                default:
                    break;
            }
            // emit updated version of cachedPosts to subscribing components
            if (PageType.OVERVIEW === this.pageType) {
                // by invoking the getFilteredPosts method with forceUpdate set to true, i.e. refetch currently displayed posts from server
                const oldPage = this.currentPostContextFilter.page;
                const oldPageSize = this.currentPostContextFilter.pageSize;
                this.currentPostContextFilter.pageSize = oldPageSize! * (oldPage! + 1);
                this.currentPostContextFilter.page = 0;
                this.getFilteredPosts(this.currentPostContextFilter);
                this.currentPostContextFilter.pageSize = oldPageSize;
                this.currentPostContextFilter.page = oldPage;
            } else {
                // by invoking the getFilteredPosts method with forceUpdate set to false, i.e. without fetching posts from server
                this.getFilteredPosts(this.currentPostContextFilter, false);
            }
        });
    }

    /**
     * Determines the channel to be used for websocket communication based on the current post context filter,
     * i.e., when being on a lecture page, the context is a certain lectureId (e.g., 1), the channel is set to '/topic/metis/lectures/1';
     * By calling the createWebsocketSubscription method with this channel as parameter, the metis service also subscribes to that messages in this channel
     */
    private createSubscriptionFromPostContextFilter(): void {
        let channel = MetisWebsocketChannelPrefix;
        if (this.currentPostContextFilter.exerciseId) {
            channel += `exercises/${this.currentPostContextFilter.exerciseId}`;
        } else if (this.currentPostContextFilter.lectureId) {
            channel += `lectures/${this.currentPostContextFilter.lectureId}`;
        } else if (this.currentPostContextFilter.conversationId) {
            channel = `/user${MetisWebsocketChannelPrefix}courses/${this.courseId}/conversations/` + this.currentPostContextFilter.conversationId;
        } else {
            // subscribe to course as this is topic that is emitted on in every case
            channel += `courses/${this.courseId}`;
        }
        this.createWebsocketSubscription(channel);
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
}
