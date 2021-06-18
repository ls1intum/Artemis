import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Post } from 'app/entities/metis/post.model';
import { User } from 'app/core/user/user.model';
import { PostingService } from 'app/overview/postings/posting.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-postings-header',
    templateUrl: './postings-header.component.html',
    styleUrls: ['../postings.scss'],
})
export class PostingsHeaderComponent implements OnInit {
    @Input() posting?: AnswerPost | Post;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() editModeChange: EventEmitter<void>;
    isAnswerPost: boolean;
    isAuthorOfPosting: boolean;
    courseId: number;

    constructor(private postingService: PostingService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.isAnswerPost = this.posting instanceof AnswerPost;
        this.isAnswerPost = this.user ? this.posting?.author!.id === this.user.id : false;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }

    getIcon(): string {
        if (this.posting instanceof Post) {
            return 'comment';
        } else {
            return 'comments';
        }
    }

    getDeleteTooltip() {
        if (this.posting instanceof Post) {
            return 'artemisApp.metis.sidebar.deletePost';
        } else {
            return 'artemisApp.metis.sidebar.deleteAnswerPost';
        }
    }

    getDeleteConfirmationText() {
        if (this.posting instanceof Post) {
            return 'artemisApp.metis.sidebar.confirmDeletePost';
        } else {
            return 'artemisApp.metis.sidebar.confirmDeleteAnswerPost';
        }
    }

    toggleEditMode() {
        this.editModeChange.emit();
    }
}
