import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import * as moment from 'moment';
import { HttpResponse } from '@angular/common/http';
import { PostRowActionName, PostRowAction } from 'app/overview/postings/post-row/post-row.component';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { Post } from 'app/entities/metis/post.model';
import { PostService } from 'app/overview/postings/post/post.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';
import interact from 'interactjs';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-postings',
    templateUrl: './postings.component.html',
    styleUrls: ['./postings.scss'],
})
export class PostingsComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;

    posts: Post[];
    isEditMode: boolean;
    isLoading = false;
    collapsed = false;
    postContent?: string;
    selectedPost?: Post;
    currentUser: User;
    isAtLeastTutorInCourse: boolean;
    EditorMode = EditorMode;
    courseId: number;

    constructor(private route: ActivatedRoute, private accountService: AccountService, private postService: PostService, private exerciseService: ExerciseService) {}

    /**
     * get the current user and check if he/she is at least a tutor for this course
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.currentUser = user;
        });
        this.loadPosts();
    }

    loadPosts() {
        if (this.exercise) {
            // in this case the posts are preloaded
            this.posts = PostingsComponent.sortPostsByVote(this.exercise.posts!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
            this.courseId = this.exercise.course!.id!;
        } else if (this.lecture) {
            // in this case the posts are preloaded
            this.posts = PostingsComponent.sortPostsByVote(this.lecture.posts!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.lecture.course!);
            this.courseId = this.lecture.course!.id!;
        }
    }

    /**
     * Configures interact to make instructions expandable
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

    /**
     * interact with actions send from postRow
     * @param {PostRowAction} action
     */
    interactPost(action: PostRowAction) {
        switch (action.name) {
            case PostRowActionName.DELETE:
                this.deletePostFromList(action.post);
                break;
            case PostRowActionName.VOTE_CHANGE:
                this.updatePostAfterVoteChange(action.post);
                break;
        }
    }

    /**
     * takes a post and removes it from the list
     * @param {Post} post
     */
    deletePostFromList(post: Post): void {
        this.posts = this.posts.filter((el) => el.id !== post.id);
    }

    /**
     * create a new post
     */
    addPost(): void {
        this.isLoading = true;
        const post = new Post();
        post.content = this.postContent;
        post.visibleForStudents = true;
        if (this.exercise) {
            post.exercise = Object.assign({}, this.exerciseService.convertExerciseForServer(this.exercise), {});
        } else {
            post.lecture = Object.assign({}, this.lecture, {});
            delete post.lecture.attachments;
            delete post.lecture.lectureUnits;
        }
        post.creationDate = moment();
        this.postService.create(this.courseId, post).subscribe({
            next: (postResponse: HttpResponse<Post>) => {
                this.posts.push(postResponse.body!);
                this.postContent = undefined;
                this.isEditMode = false;
            },
            error: () => {
                this.isLoading = false;
            },
            complete: () => {
                this.isLoading = false;
            },
        });
    }

    private static sortPostsByVote(posts: Post[]): Post[] {
        return posts.sort((a, b) => {
            return b.votes! - a.votes!;
        });
    }

    private updatePostAfterVoteChange(upvotedPost: Post): void {
        const indexToUpdate = this.posts.findIndex((post) => {
            return post.id === upvotedPost.id;
        });
        this.posts[indexToUpdate] = upvotedPost;
        this.posts = PostingsComponent.sortPostsByVote(this.posts);
    }
}
