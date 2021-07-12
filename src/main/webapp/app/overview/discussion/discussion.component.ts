import { AfterViewInit, Component, Input, OnInit } from '@angular/core';
import { User } from 'app/core/user/user.model';
import { PostRowAction, PostRowActionName } from 'app/shared/metis/postings-thread/postings-thread.component';
import { Lecture } from 'app/entities/lecture.model';
import { AccountService } from 'app/core/auth/account.service';
import { Post } from 'app/entities/metis/post.model';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import interact from 'interactjs';
import { ActivatedRoute } from '@angular/router';
import { PostService } from 'app/shared/metis/post/post.service';

@Component({
    selector: 'jhi-discussion',
    templateUrl: './discussion.component.html',
    styleUrls: ['./discussion.scss'],
})
export class DiscussionComponent implements OnInit, AfterViewInit {
    @Input() exercise: Exercise;
    @Input() lecture: Lecture;
    posts: Post[];
    collapsed = false;
    user: User;
    isAtLeastTutorInCourse: boolean;
    courseId: number;
    existingPostTags: string[];

    constructor(private route: ActivatedRoute, private accountService: AccountService, private exerciseService: ExerciseService, private postService: PostService) {}

    /**
     * get the current user and check if he/she is at least a tutor for this course
     */
    ngOnInit(): void {
        this.accountService.identity().then((user: User) => {
            this.user = user!;
        });
        this.loadPosts();
        this.loadExistingPostTags();
    }

    loadPosts() {
        if (this.exercise) {
            this.posts = DiscussionComponent.sortPostsByVote(this.exercise.posts!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.exercise.course!);
            this.courseId = this.exercise.course!.id!;
        } else if (this.lecture) {
            this.posts = DiscussionComponent.sortPostsByVote(this.lecture.posts!);
            this.isAtLeastTutorInCourse = this.accountService.isAtLeastTutorInCourse(this.lecture.course!);
            this.courseId = this.lecture.course!.id!;
        }
    }

    loadExistingPostTags() {
        this.postService.getAllPostTags(this.courseId).subscribe((tags: string[]) => {
            this.existingPostTags = tags;
        });
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

    /**
     * interact with actions send from postRow
     * @param {PostRowAction} action
     */
    interactPost(action: PostRowAction) {
        switch (action.name) {
            case PostRowActionName.VOTE_CHANGE:
                this.updatePostAfterVoteChange(action.post);
                break;
        }
    }

    deletePost(post: Post): void {
        this.posts = this.posts.filter((el) => el.id !== post.id);
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
        this.posts = DiscussionComponent.sortPostsByVote(this.posts);
    }

    onCreatePost(post: Post): void {
        this.posts.push(post);
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
}
