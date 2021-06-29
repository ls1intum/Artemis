import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { ActivatedRoute } from '@angular/router';
import { PostService } from 'app/overview/postings/post/post.service';
import { PostAction, PostActionName } from 'app/overview/postings/post/post.component';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../postings.scss'],
})
export class PostHeaderComponent implements OnInit {
    @Input() post: Post;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() editModeChange: EventEmitter<void> = new EventEmitter();
    @Output() interactPost: EventEmitter<PostAction> = new EventEmitter<PostAction>();
    isAuthorOfPost: boolean;
    courseId: number;

    constructor(private postService: PostService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.isAuthorOfPost = this.user ? this.post.author!.id === this.user.id : false;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }

    toggleEditMode() {
        this.editModeChange.emit();
    }

    /**
     * pass the post to the row to delete
     */
    deletePost(): void {
        this.interactPost.emit({
            name: PostActionName.DELETE,
            post: this.post,
        });
    }
}
