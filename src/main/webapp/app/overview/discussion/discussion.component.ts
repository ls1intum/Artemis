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
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;
    course: Course;
    courseId: number;
    posts: Post[];
    collapsed = false;
    createdPost: Post;

    private postsSubscription: Subscription;

    constructor(private metisService: MetisService, private exerciseService: ExerciseService) {
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.posts = posts;
        });
    }

    ngOnInit(): void {
        this.resetCourseAndPosts();
    }

    ngOnChanges(): void {
        this.resetCourseAndPosts();
    }

    private resetCourseAndPosts(): void {
        this.course = this.exercise ? this.exercise.course! : this.lecture.course!;
        this.courseId = this.course.id!;
        this.metisService.setCourse(this.course);
        this.metisService.getPostsForFilter({ exercise: this.exercise, lecture: this.lecture });
        this.createdPost = this.createEmptyPost();
    }

    /**
     * Configures interact to make discussion expandable
     */
    ngAfterViewInit(): void {
        interact('.expanded-posts')
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
                id: this.lecture.id,
            };
        }
        return post;
    }

    postsTrackByFn(index: number, post: Post): number {
        return post.id!;
    }

    ngOnDestroy() {
        this.postsSubscription.unsubscribe();
    }

    onCreatePost() {
        this.createdPost = this.createEmptyPost();
    }
}
