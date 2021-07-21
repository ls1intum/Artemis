import { CourseWideContext, Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/shared/metis/post/post.service';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { BehaviorSubject, map, Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { Course } from 'app/entities/course.model';
import { Posting } from 'app/entities/metis/posting.model';
import { Injectable } from '@angular/core';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

interface PostFilter {
    exercise?: Exercise;
    lecture?: Lecture;
    courseWideContext?: CourseWideContext;
}

@Injectable()
export class MetisService {
    private posts$: BehaviorSubject<Post[]> = new BehaviorSubject<Post[]>([]);
    private tags$: BehaviorSubject<string[]> = new BehaviorSubject<string[]>([]);
    private courseId: number;
    private currentPostFilter?: PostFilter;
    private user: User;
    private course: Course;

    constructor(private postService: PostService, private answerPostService: AnswerPostService, private accountService: AccountService) {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
    }

    get posts(): Observable<Post[]> {
        return this.posts$.asObservable();
    }

    get tags(): Observable<string[]> {
        return this.tags$;
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

    updateCoursePostTags() {
        this.postService.getAllPostTagsByCourseId(this.courseId).subscribe((res: HttpResponse<string[]>) => {
            this.tags$.next(res.body!.filter((t) => !!t));
        });
    }

    getPostsForFilter(postFilter?: PostFilter): void {
        this.currentPostFilter = postFilter;
        if (postFilter?.lecture) {
            this.postService.getAllPostsByLectureId(this.courseId, postFilter.lecture.id!).subscribe((res: HttpResponse<Post[]>) => {
                this.posts$.next(this.sortPostsByVote(res.body!));
            });
        } else if (postFilter?.exercise) {
            this.postService.getAllPostsByExerciseId(this.courseId, postFilter.exercise.id!).subscribe((res: HttpResponse<Post[]>) => {
                this.posts$.next(this.sortPostsByVote(res.body!));
            });
        } else if (postFilter?.courseWideContext) {
            this.postService.getAllPostsByCourseId(this.courseId).subscribe((res: HttpResponse<Post[]>) => {
                const posts = res.body!.filter((post) => post.courseWideContext === postFilter.courseWideContext);
                this.posts$.next(posts);
            });
        } else {
            this.postService.getAllPostsByCourseId(this.courseId).subscribe((res: HttpResponse<Post[]>) => {
                this.posts$.next(this.sortPostsByVote(res.body!));
            });
        }
    }

    deletePost(post: Post): void {
        this.postService.delete(this.courseId, post).subscribe(() => {
            this.getPostsForFilter(this.currentPostFilter);
        });
    }

    deleteAnswerPost(answerPost: AnswerPost): void {
        this.answerPostService.delete(this.courseId, answerPost).subscribe(() => {
            this.getPostsForFilter(this.currentPostFilter);
        });
    }

    createPost(post: Post): Observable<Post> {
        return this.postService.create(this.courseId, post).pipe(
            map((res) => {
                this.getPostsForFilter(this.currentPostFilter);
                return res.body!;
            }),
        );
    }

    createAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.create(this.courseId, answerPost).pipe(
            map((res) => {
                this.getPostsForFilter(this.currentPostFilter);
                return res.body!;
            }),
        );
    }

    updatePost(post: Post): Observable<Post> {
        return this.postService.update(this.courseId, post).pipe(
            map((res) => {
                this.getPostsForFilter(this.currentPostFilter);
                return res.body!;
            }),
        );
    }

    updateAnswerPost(answerPost: AnswerPost): Observable<AnswerPost> {
        return this.answerPostService.update(this.courseId, answerPost).pipe(
            map((res) => {
                this.getPostsForFilter(this.currentPostFilter);
                return res.body!;
            }),
        );
    }

    updatePostVotes(post: Post, voteChange: number): void {
        this.postService.updateVotes(this.courseId, post.id!, voteChange).subscribe(() => {
            this.getPostsForFilter(this.currentPostFilter);
        });
    }

    /**
     * Use this to set posts from outside.
     * @param posts
     */
    setPosts(posts: Post[]): void {
        this.posts$.next(posts);
    }

    metisUserIsAtLeastTutorInCourse(): boolean {
        return this.accountService.isAtLeastTutorInCourse(this.course);
    }

    metisUserIsAuthorOfPosting(posting: Posting): boolean {
        return this.user ? posting?.author!.id === this.user.id : false;
    }

    private sortPostsByVote(posts: Post[]): Post[] {
        return posts.sort((a, b) => {
            return b.votes! - a.votes!;
        });
    }
}
