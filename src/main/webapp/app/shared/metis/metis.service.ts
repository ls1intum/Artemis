import { CourseWideContext, Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post.service';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
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

interface PostFilter {
    exercise?: Exercise;
    lecture?: Lecture;
    courseWideContext?: CourseWideContext;
}

export const VOTE_EMOJI_ID = 'heavy_plus_sign';

@Injectable()
export class MetisService {
    private posts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private courseId: number;
    private currentPostFilter?: PostFilter;
    private user: User;
    private course: Course;

    constructor(private postService: PostService, private answerPostService: AnswerPostService, private reactionService: ReactionService, private accountService: AccountService) {
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

    getUser(): User {
        return this.user;
    }

    getCourse(): Course {
        return this.course;
    }

    setCourse(course: Course) {
        this.course = course;
        this.courseId = course.id!;
        this.updateCoursePostTags();
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
     * @param postFilter criteria to filter course posts with (lecture, exercise, course-wide context)
     */
    getPostsForFilter(postFilter?: PostFilter): void {
        this.currentPostFilter = postFilter;
        if (postFilter?.lecture) {
            this.postService
                .getAllPostsByLectureId(this.courseId, postFilter.lecture.id!)
                .pipe(map((res: HttpResponse<Post[]>) => MetisService.sortPosts(res.body!)))
                .subscribe((posts: Post[]) => {
                    this.posts$.next(posts);
                });
        } else if (postFilter?.exercise) {
            this.postService
                .getAllPostsByExerciseId(this.courseId, postFilter.exercise.id!)
                .pipe(map((res: HttpResponse<Post[]>) => MetisService.sortPosts(res.body!)))
                .subscribe((posts: Post[]) => {
                    this.posts$.next(posts);
                });
        } else {
            this.postService
                .getAllPostsByCourseId(this.courseId)
                .pipe(
                    map((res: HttpResponse<Post[]>) => {
                        let posts: Post[] = res.body!;
                        if (postFilter?.courseWideContext) {
                            posts = posts.filter((post) => post.courseWideContext === postFilter.courseWideContext);
                        }
                        return MetisService.sortPosts(posts);
                    }),
                )
                .subscribe((posts: Post[]) => {
                    this.posts$.next(posts);
                });
        }
    }

    /**
     * creates a new post by invoking the post service
     * fetches the post for the currently set filter on response  and updates course tags
     * @param post newly created post
     */
    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * creates a new answer post by invoking the answer post service
     * fetches the post for the currently set filter on response
     * @param answerPost newly created answer post
     */
    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given answer posts by invoking the answer post service,
     * fetches the post for the currently set filter on response
     * @param post post to update
     */
    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
                this.updateCoursePostTags();
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates a given answer posts by invoking the answer post service,
     * fetches the post for the currently set filter on response
     * @param answerPost answer post to update
     */
    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates the pin state of a post by invoking the post service
     * @param post      post for which the pin state is toggled
     * @param pinState  updated pin state
     */
    updatePostPinState(post: Post, pinState: boolean): Observable<Post> {
        return this.postService.updatePinState(this.courseId, post.id!, pinState).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
            }),
            map((res: HttpResponse<Post>) => res.body!),
        );
    }

    /**
     * updates the archive state of a post by invoking the post service
     * @param post          post for which the archive state is toggled
     * @param archiveState  updated archive state
     */
    updatePostArchiveState(post: Post, archiveState: boolean): Observable<Post> {
        return this.postService.updateArchiveState(this.courseId, post.id!, archiveState).pipe(
            tap(() => {
                this.getPostsForFilter(this.currentPostFilter);
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
            this.getPostsForFilter(this.currentPostFilter);
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
            this.getPostsForFilter(this.currentPostFilter);
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
                this.getPostsForFilter(this.currentPostFilter);
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
                this.getPostsForFilter(this.currentPostFilter);
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

    /**
     * sorts posts by two criteria
     * 1. criterion: pin -> pinned posts come first
     * 2. criterion: vote-emoji count -> posts with more vote-emoji counts comes first
     * 3. criterion: archive -> archived posts come last
     * 4. criterion: creationDate -> most recent comes at the end (chronologically from top to bottom)
     * @return Post[] sorted array of posts
     */
    static sortPosts(posts: Post[]): Post[] {
        return posts.sort(function (postA, postB) {
            const postAVoteEmojiCount = postA.reactions?.filter((reaction) => reaction.emojiId === VOTE_EMOJI_ID).length
                ? postA.reactions?.filter((reaction) => reaction.emojiId === VOTE_EMOJI_ID).length
                : 0;
            const postBVoteEmojiCount = postB.reactions?.filter((reaction) => reaction.emojiId === VOTE_EMOJI_ID).length ?? 0;
            if (postA.pinned && !postB.pinned) {
                return -1;
            }
            if (!postA.pinned && postB.pinned) {
                return 1;
            }
            if (postAVoteEmojiCount > postBVoteEmojiCount) {
                return -1;
            }
            if (postAVoteEmojiCount < postBVoteEmojiCount) {
                return 1;
            }
            if (postA.archived && !postB.archived) {
                return 1;
            }
            if (!postA.archived && postB.archived) {
                return -1;
            }
            if (Number(postA.creationDate) > Number(postB.creationDate)) {
                return 1;
            }
            if (Number(postA.creationDate) < Number(postB.creationDate)) {
                return -1;
            }
            return 0;
        });
    }
}
