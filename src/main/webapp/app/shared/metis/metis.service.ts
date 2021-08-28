import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { BehaviorSubject, map, Observable, tap } from 'rxjs';
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
import { DisplayPriority, PageType, PostContextFilter, PostSortFilter } from 'app/shared/metis/metis.util';

@Injectable()
export class MetisService {
    private posts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private currentPostContextFilter: PostContextFilter = {};
    private currentPostSortFilter: PostSortFilter = {};
    private user: User;
    private pageType: PageType = PageType.OVERVIEW;
    private course: Course;
    private courseId: number;

    constructor(
        protected postService: PostService,
        protected answerPostService: AnswerPostService,
        protected reactionService: ReactionService,
        protected accountService: AccountService,
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
     * Set course property before using metis service.
     * @param course
     */
    setCourse(course: Course) {
        if (this.courseId === undefined || this.courseId !== course.id) {
            this.courseId = course.id!;
            this.course = course;
            this.updateCoursePostTags();
        }
    }

    /**
     * to be used to set posts from outside
     * @param posts
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
     * informs all components that subscribed on posts by sending out the sorted, newly fetched posts
     * @param postContextFilter criteria to filter course posts with (lecture, exercise, course-wide context)
     * @param postSortFilter criteria to filter course posts with (lecture, exercise, course-wide context)
     * @param forceReload
     */
    getFilteredAndSortedPosts(postContextFilter: PostContextFilter, postSortFilter: PostSortFilter, forceReload = true): void {
        this.currentPostSortFilter = postSortFilter;
        // check if the post context did change
        if (
            forceReload ||
            postContextFilter?.courseId !== this.currentPostContextFilter?.courseId ||
            postContextFilter?.courseWideContext !== this.currentPostContextFilter?.courseWideContext ||
            postContextFilter?.lectureId !== this.currentPostContextFilter?.lectureId ||
            postContextFilter?.exerciseId !== this.currentPostContextFilter?.exerciseId
        ) {
            // if the context changed, we need to fetch posts before doing the content filtering and sorting
            this.currentPostContextFilter = postContextFilter;
            this.postService
                .getPosts(this.courseId, this.currentPostContextFilter)
                .pipe(
                    map((res: HttpResponse<Post[]>) => {
                        return this.filterAndSortIfSpecified(res.body!, postSortFilter);
                    }),
                )
                .subscribe((posts) => {
                    this.posts$.next(posts);
                });
        } else {
            // if the context did not change, we do not need to fetch posts again but only do the content filtering and sorting the current posts
            this.posts$.next(this.filterAndSortIfSpecified(this.posts$.getValue(), postSortFilter));
        }
    }

    /**
     * creates a new post by invoking the post service
     * fetches the posts for the currently set filter on response and updates course tags
     * @param post newly created post
     */
    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * creates a new answer post by invoking the answer post service
     * fetches the posts for the currently set filter on response
     * @param answerPost newly created answer post
     */
    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given posts by invoking the post service,
     * fetches the posts for the currently set filter on response and updates course tags
     * @param post post to update
     */
    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given answer posts by invoking the answer post service,
     * fetches the posts for the currently set filter on response
     * @param answerPost answer post to update
     */
    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates the display priority of a post to NONE, PINNED, ARCHIVED
     * @param postId            id of the post for which the displayPriority is changed
     * @param displayPriority   new displayPriority
     */
    updatePostDisplayPriority(postId: number, displayPriority: DisplayPriority): Observable<Post> {
        return this.postService.updatePostDisplayPriority(this.courseId, postId, displayPriority).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * deletes a post by invoking the post service
     * fetches the posts for the currently set filter on response and updates course tags
     * @param post post to delete
     */
    deletePost(post: Post): void {
        this.postService.delete(this.courseId, post).subscribe(() => {
            this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            this.updateCoursePostTags();
        });
    }

    /**
     * deletes an answer post by invoking the post service
     * fetches the posts for the currently set filter on response
     * @param answerPost answer post to delete
     */
    deleteAnswerPost(answerPost: AnswerPost): void {
        this.answerPostService.delete(this.courseId, answerPost).subscribe(() => {
            this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
        });
    }

    /**
     * creates a new reaction
     * fetches the posts for the currently set filter on response
     * @param reaction reaction to create
     */
    createReaction(reaction: Reaction): Observable<Reaction> {
        return this.reactionService.create(this.courseId, reaction).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * deletes an existing reaction
     * fetches the posts for the currently set filter on response
     * @param reaction reaction to create
     */
    deleteReaction(reaction: Reaction): Observable<void> {
        return this.reactionService.delete(this.courseId, reaction).pipe(
            tap(() => {
                this.getFilteredAndSortedPosts(this.currentPostContextFilter, this.currentPostSortFilter);
            }),
            map((res: HttpResponse<void>) => res.body!),
        );
    }

    /**
     * determines if the current user is at least tutor in the current course
     * @return boolean tutor flag
     */
    metisUserIsAtLeastTutorInCourse(): boolean {
        return this.accountService.isAtLeastTutorInCourse(this.course);
    }

    /**
     * determines if the current user is the author of a given posting
     * @param posting posting to be checked against
     * @return boolean author flag
     */
    metisUserIsAuthorOfPosting(posting: Posting): boolean {
        return this.user ? posting?.author!.id === this.getUser().id : false;
    }

    private filterAndSortIfSpecified(posts: Post[], postSortFilter?: PostSortFilter): Post[] {
        if (postSortFilter?.filter) {
            posts = posts.filter(postSortFilter.filter);
        }
        if (postSortFilter?.sort) {
            posts = posts.sort(postSortFilter.sort);
        }
        return posts;
    }
}
