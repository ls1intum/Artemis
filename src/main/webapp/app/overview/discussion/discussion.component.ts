import { AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { Post } from 'app/entities/metis/post.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import interact from 'interactjs';
import { MetisService } from 'app/shared/metis/metis.service';
import { Subscription } from 'rxjs';
import { Course } from 'app/entities/course.model';

@Component({
    selector: 'jhi-discussion',
    templateUrl: './discussion.component.html',
    styleUrls: ['./discussion.scss'],
    providers: [MetisService],
})
export class DiscussionComponent implements OnInit, AfterViewInit, OnChanges, OnDestroy {
    @Input() exercise?: Exercise;
    @Input() lecture?: Lecture;
    course: Course;
    courseId: number;
    posts: Post[];
    collapsed = false;
    createdPost: Post;

    private postsSubscription: Subscription;

    constructor(private metisService: MetisService, private exerciseService: ExerciseService) {}

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
        this.course = this.exercise ? this.exercise.course! : this.lecture!.course!;
        this.courseId = this.course.id!;
        this.metisService.setCourse(this.course);
        this.metisService.getPostsForFilter({ exercise: this.exercise, lecture: this.lecture });
        this.createdPost = this.createEmptyPost();
    }

    /**
     * makes discussion expandable by configuring 'interact'
     */
    ngAfterViewInit(): void {
        interact('.expanded-discussion')
            .resizable({
                edges: { left: '.draggable-left', right: false, bottom: false, top: false },
                modifiers: [
                    // Set maximum width
                    interact.modifiers!.restrictSize({
                        min: { width: 300, height: 0 },
                        max: { width: 600, height: 4000 },
                    }),
                ],
                inertia: true,
            })
            .on('resizestart', function (event: any) {
                event.target.classList.add('card-resizable');
            })
            .on('resizeend', function (event: any) {
                event.target.classList.remove('card-resizable');
            })
            .on('resizemove', function (event: any) {
                const target = event.target;
                target.style.width = event.rect.width + 'px';
            });
    }

    /**
     * creates empty default post that is needed on initialization of a newly opened modal to edit or create a post
     * @return Post created empty default post
     */
    createEmptyPost(): Post {
        const post = new Post();
        post.content = '';
        post.visibleForStudents = true;
        if (this.exercise) {
            post.exercise = {
                ...this.exerciseService.convertExerciseForServer(this.exercise),
            };
        } else {
            post.lecture = {
                id: this.lecture!.id,
            };
        }
        return post;
    }

    /**
     * defines a function that returns the post id as unique identifier,
     * by this means, Angular determines which post in the collection of posts has to be reloaded/destroyed on changes
     */
    postsTrackByFn(index: number, post: Post): number {
        return post.id!;
    }

    /**
     * resets createdPost to a new default post after a post was successfully created
     */
    onCreatePost(): void {
        this.createdPost = this.createEmptyPost();
    }

    ngOnDestroy(): void {
        this.postsSubscription.unsubscribe();
    }
}
