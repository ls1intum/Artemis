import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { BehaviorSubject, map, Observable, ReplaySubject, tap } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';
import { Injectable } from '@angular/core';
import { AnswerPostService } from 'app/shared/metis/answer-post.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ReactionService } from 'app/shared/metis/reaction.service';
import { ContextInformation, CourseWideContext, DisplayPriority, PageType, PostContextFilter } from 'app/shared/metis/metis.util';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { Params } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';

@Injectable()
export class MetisService {
    private posts$: ReplaySubject<Post[]> = new ReplaySubject<Post[]>(1);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private currentPostContextFilter: PostContextFilter = {};
    private user: User;
    private pageType: PageType;
    private course: Course;
    private courseId: number;
    private cachedPosts: Post[];

    constructor(
        protected postService: PostService,
        protected answerPostService: AnswerPostService,
        protected reactionService: ReactionService,
        protected accountService: AccountService,
        protected exerciseService: ExerciseService,
        private translateService: TranslateService,
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
        // check if the post context did change
        if (
            forceUpdate ||
            postContextFilter?.courseId !== this.currentPostContextFilter?.courseId ||
            postContextFilter?.courseWideContext !== this.currentPostContextFilter?.courseWideContext ||
            postContextFilter?.lectureId !== this.currentPostContextFilter?.lectureId ||
            postContextFilter?.exerciseId !== this.currentPostContextFilter?.exerciseId
        ) {
            // if the context changed, we need to fetch posts before doing the content filtering and sorting
            this.currentPostContextFilter = postContextFilter;
            this.postService.getPosts(this.courseId, this.currentPostContextFilter).subscribe((res) => {
                // cache the fetched posts, that can be emitted on next call of this `getFilteredPosts`
                // that does not require to send a request to actually fetch posts from the DB
                this.cachedPosts = res.body!;
                this.posts$.next(res.body!);
            });
        } else {
            // if we do not require force update, e.g. because only the sorting criterion changed,
            // we can emit the previously cached posts
            this.posts$.next(this.cachedPosts);
        }
    }

    /**
     * creates a new post by invoking the post service
     * fetches the posts for the currently set filter on response and updates course tags
     * @param {Post} post to be created
     * @return {Observable<Post>} created post
     */
    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * creates a new answer post by invoking the answer post service
     * fetches the posts for the currently set filter on response
     * @param {AnswerPost} answerPost to be created
     * @return {Observable<AnswerPost>} created answer post
     */
    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given posts by invoking the post service,
     * fetches the posts for the currently set filter on response and updates course tags
     * @param {Post} post to be updated
     * @return {Observable<Post>} updated post
     */
    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given answer posts by invoking the answer post service,
     * fetches the posts for the currently set filter on response
     * @param {AnswerPost} answerPost to be updated
     * @return {Observable<AnswerPost>} updated answer post
     */
    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates the display priority of a post to NONE, PINNED, ARCHIVED
     * @param {number} postId id of the post for which the displayPriority is changed
     * @param {DisplayPriority} displayPriority new displayPriority
     * @return {Observable<Post>} updated post
     */
    updatePostDisplayPriority(postId: number, displayPriority: DisplayPriority): Observable<Post> {
        return this.postService.updatePostDisplayPriority(this.courseId, postId, displayPriority).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * deletes a post by invoking the post service
     * fetches the posts for the currently set filter on response and updates course tags
     * @param {Post} post to be deleted
     */
    deletePost(post: Post): void {
        this.postService.delete(this.courseId, post).subscribe(() => {
            this.getFilteredPosts(this.currentPostContextFilter);
            this.updateCoursePostTags();
        });
    }

    /**
     * deletes an answer post by invoking the post service
     * fetches the posts for the currently set filter on response
     * @param {AnswerPost} answerPost to be deleted
     */
    deleteAnswerPost(answerPost: AnswerPost): void {
        this.answerPostService.delete(this.courseId, answerPost).subscribe(() => {
            this.getFilteredPosts(this.currentPostContextFilter);
        });
    }

    /**
     * creates a new reaction
     * fetches the posts for the currently set filter on response
     * @param {Reaction} reaction to be created
     * @return {Observable<Reaction>} created reaction
     */
    createReaction(reaction: Reaction): Observable<Reaction> {
        return this.reactionService.create(this.courseId, reaction).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * deletes an existing reaction
     * fetches the posts for the currently set filter on response
     * @param {Reaction} reaction to be deleted
     */
    deleteReaction(reaction: Reaction): Observable<void> {
        return this.reactionService.delete(this.courseId, reaction).pipe(
            tap(() => {
                this.getFilteredPosts(this.currentPostContextFilter);
            }),
            map((res: HttpResponse<void>) => res.body!),
        );
    }

    /**
     * determines if the current user is at least tutor in the current course
     * @return {boolean} tutor flag
     */
    metisUserIsAtLeastTutorInCourse(): boolean {
        return this.accountService.isAtLeastTutorInCourse(this.course);
    }

    /**
     * determines if the current user is at least instructor in the current course
     * @return boolean instructor flag
     */
    metisUserIsAtLeastInstructorInCourse(): boolean {
        return this.accountService.isAtLeastInstructorInCourse(this.course);
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
     * @param {number | undefined} lectureId id of optional lecture as default context
     * @return {Post} created default object
     */
    createEmptyPostForContext(courseWideContext?: CourseWideContext, exercise?: Exercise, lectureId?: number): Post {
        const emptyPost: Post = new Post();
        if (courseWideContext) {
            emptyPost.courseWideContext = courseWideContext;
            emptyPost.course = this.course;
        } else if (exercise) {
            const exercisePost = this.exerciseService.convertExerciseForServer(exercise);
            emptyPost.exercise = { id: exercisePost.id, title: exercisePost.title, type: exercisePost.type } as Exercise;
        } else if (lectureId) {
            emptyPost.lecture = { id: lectureId } as Lecture;
        } else {
            // set default
            emptyPost.courseWideContext = CourseWideContext.TECH_SUPPORT as CourseWideContext;
        }
        return emptyPost;
    }

    /**
     * counts the answer posts of a post, 0 if none exist
     * @param {Post} post of which the answer posts are counted
     * @return {number} amount of answer posts
     */
    getNumberOfAnswerPosts(post: Post): number {
        return post.answers?.length! ? post.answers?.length! : 0;
    }

    /**
     * determines if the post is resolved by searching for resolving answer posts
     * @param {Post} post of which the state is determined
     * @return {boolean} flag that indicates if the post is resolved
     */
    isPostResolved(post: Post): boolean {
        if (this.getNumberOfAnswerPosts(post) > 0) {
            return post.answers!.filter((answer: AnswerPost) => answer.resolvesPost === true).length > 0;
        } else {
            return false;
        }
    }

    /**
     * determines the router link components required for navigating to the detail view of the given post
     * @param {Post} post to be navigated to
     * @return {(string | number)[]} array of router link components
     */
    getLinkForPost(post?: Post): (string | number)[] {
        if (post?.lecture) {
            return ['/courses', this.courseId, 'lectures', post.lecture.id!];
        }
        if (post?.exercise) {
            return ['/courses', this.courseId, 'exercises', post.exercise.id!];
        }
        return ['/courses', this.courseId, 'discussion'];
    }

    /**
     * determines the routing params required for navigating to the detail view of the given post
     * @param {Post} post to be navigated to
     * @return {Params} required parameter key-value pair
     */
    getQueryParamsForPost(post: Post): Params {
        const params: Params = {};
        if (post.courseWideContext) {
            params.searchText = `#${post.id}`;
        } else {
            params.postId = post.id;
        }
        return params;
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
}
