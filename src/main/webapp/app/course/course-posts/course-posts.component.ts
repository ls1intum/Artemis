import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Post } from 'app/entities/metis/post.model';
import { SortService } from 'app/shared/service/sort.service';
import { Moment } from 'moment';
import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { PostService } from 'app/shared/metis/post.service';

export type PostForOverview = {
    id: number;
    content?: string;
    creationDate?: Moment;
    votes: number;
    answers: number;
    approvedAnswerPosts: number;
    exerciseOrLectureId: number;
    exerciseOrLectureTitle: string;
    belongsToExercise: boolean;
    exercise: Exercise;
    lecture: Lecture;
};

@Component({
    selector: 'jhi-course-posts',
    styles: ['.question-cell { max-width: 40vw; max-height: 120px; overflow: auto;}'],
    templateUrl: './course-posts.component.html',
})
export class CoursePostsComponent implements OnInit {
    courseId: number;
    posts: PostForOverview[];
    postsToDisplay: PostForOverview[];

    showPostsWithApprovedAnswerPosts = false;

    predicate = 'id';
    reverse = true;

    constructor(private route: ActivatedRoute, private postService: PostService, private sortService: SortService) {}

    /**
     * On init fetch the course and the posts
     * convert posts to PostForOverview type to allow sorting by all displayed fields
     */
    ngOnInit() {
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.postService.getAllPostsByCourseId(this.courseId).subscribe((res) => {
            this.posts = res.body!.map((post: Post) => ({
                id: post.id!,
                content: post.content!,
                creationDate: post.creationDate!,
                answers: post.answers ? post.answers!.length : 0,
                approvedAnswerPosts: this.getNumberOfApprovedAnswerPosts(post),
                votes: post.votes!,
                exerciseOrLectureId: post.exercise ? post.exercise!.id! : post.lecture!.id!,
                exerciseOrLectureTitle: post.exercise ? post.exercise!.title! : post.lecture!.title!,
                belongsToExercise: !!post.exercise,
                exercise: post.exercise!,
                lecture: post.lecture!,
            }));
            this.postsToDisplay = this.posts.filter((post) => post.approvedAnswerPosts === 0);
        });
    }

    /**
     * returns the number of approved answer posts for a post
     * @param { Post }post
     */
    getNumberOfApprovedAnswerPosts(post: Post): number {
        return post.answers ? post.answers.filter((answerPost) => answerPost.tutorApproved).length : 0;
    }

    sortRows() {
        this.sortService.sortByProperty(this.postsToDisplay, this.predicate, this.reverse);
    }

    /**
     * removes all posts with approved answers from posts to display
     */
    hidePostsWithApprovedAnswerPosts(): void {
        this.postsToDisplay = this.posts.filter((post) => post.approvedAnswerPosts === 0);
    }

    /**
     * toggles showing posts with approved answers and sets the posts to display
     */
    toggleHidePosts(): void {
        if (!this.showPostsWithApprovedAnswerPosts) {
            this.postsToDisplay = this.posts;
        } else {
            this.hidePostsWithApprovedAnswerPosts();
        }
        this.showPostsWithApprovedAnswerPosts = !this.showPostsWithApprovedAnswerPosts;
    }
}
