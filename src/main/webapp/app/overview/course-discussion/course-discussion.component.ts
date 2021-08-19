import { Component, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { Post } from 'app/entities/metis/post.model';
import { Exercise } from 'app/entities/exercise.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { CourseScoreCalculationService } from 'app/overview/course-score-calculation.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-course-discussion',
    templateUrl: './course-discussion.component.html',
    styleUrls: ['./course-discussion.scss'],
    providers: [MetisService],
})
export class CourseDiscussionComponent implements OnInit, OnChanges, OnDestroy {
    course?: Course;
    courseId?: number;
    posts: Post[];
    lectures: Lecture[];
    exercises: Exercise[];
    createdPost: Post;

    private paramSubscription?: Subscription;
    private postsSubscription: Subscription;

    constructor(private metisService: MetisService, private route: ActivatedRoute, private courseCalculationService: CourseScoreCalculationService) {}

    /**
     * on initialization: resets course and posts
     */
    ngOnInit(): void {
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.posts = posts;
        });
        this.resetCourseAndPosts();
    }

    /**
     * on changes: resets course and posts
     */
    ngOnChanges(): void {
        this.resetCourseAndPosts();
    }

    /**
     * @private sets the course that is associated with the exercise or lecture given as input, triggers method on metis service to fetch (an push back via subscription)
     * exercise or lecture posts respectively, creates an empty default post
     */
    private resetCourseAndPosts(): void {
        this.paramSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = parseInt(params['courseId'], 10);
            this.course = this.courseCalculationService.getCourse(this.courseId);
        });
        if (this.course) {
            this.metisService.setCourse(this.course);
            this.metisService.getPostsForFilter();
        }
        this.createdPost = this.createEmptyPost();
    }

    /**
     * creates empty default post that is needed on initialization of a newly opened modal to edit or create a post
     * @return Post created empty default post
     */
    createEmptyPost(): Post {
        const post = new Post();
        post.content = '';
        post.visibleForStudents = true;
        post.course = this.course;
        return post;
    }

    /**
     * resets createdPost to a new default post after a post was successfully created
     */
    onCreatePost(): void {
        this.createdPost = this.createEmptyPost();
    }

    /**
     * defines a function that returns the post id as unique identifier,
     * by this means, Angular determines which post in the collection of posts has to be reloaded/destroyed on changes
     */
    postsTrackByFn(index: number, post: Post): number {
        return post.id!;
    }

    ngOnDestroy(): void {
        this.postsSubscription.unsubscribe();
        if (this.paramSubscription) {
            this.paramSubscription.unsubscribe();
        }
    }
}
