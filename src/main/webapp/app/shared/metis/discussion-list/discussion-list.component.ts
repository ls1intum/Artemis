import { MetisService } from 'app/shared/metis/metis.service';
import { Course } from 'app/entities/course.model';
import { Post } from 'app/entities/metis/post.model';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { PageType } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-discussion-list',
    templateUrl: './discussion-list.component.html',
})
export class DiscussionListComponent implements OnInit, OnChanges, OnDestroy {
    @Input() exercise?: Exercise;
    @Input() lecture?: Lecture;
    @Input() course: Course;
    posts: Post[];
    createdPost: Post;
    pageType: PageType;
    ePageType = PageType;

    private postsSubscription: Subscription;

    constructor(private metisService: MetisService, private route: ActivatedRoute, private exerciseService: ExerciseService) {
        this.postsSubscription = this.metisService.posts.subscribe((posts: Post[]) => {
            this.posts = posts;
        });
    }

    /**
     * on initialization: resets course and posts
     */
    ngOnInit(): void {
        this.metisService.getPostsForFilter({ exercise: this.exercise, lecture: this.lecture });
        this.pageType = this.metisService.getPageType();
        this.createdPost = this.createEmptyPost();
    }

    /**
     * on changes: resets course and posts
     */
    ngOnChanges(): void {
        this.metisService.getPostsForFilter({ exercise: this.exercise, lecture: this.lecture });
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
        if (this.exercise) {
            post.exercise = {
                ...this.exerciseService.convertExerciseForServer(this.exercise),
            };
        } else if (this.lecture) {
            post.lecture = {
                id: this.lecture!.id,
            };
        }
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
        this.postsSubscription?.unsubscribe();
    }
}
