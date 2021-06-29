import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';
import { ActivatedRoute } from '@angular/router';
import { AnswerPostService } from 'app/overview/postings/answer-post/answer-post.service';
import { AnswerPostAction, AnswerPostActionName } from 'app/overview/postings/answer-post/answer-post.component';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../postings.scss'],
})
export class AnswerPostHeaderComponent implements OnInit {
    @Input() answerPost: AnswerPost;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() editModeChange: EventEmitter<void> = new EventEmitter();
    @Output() interactAnswerPost: EventEmitter<AnswerPostAction> = new EventEmitter<AnswerPostAction>();
    isAuthorOfAnswerPost: boolean;
    courseId: number;

    constructor(private answerPostService: AnswerPostService, private route: ActivatedRoute) {}

    ngOnInit(): void {
        this.isAuthorOfAnswerPost = this.user ? this.answerPost?.author!.id === this.user.id : false;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }

    toggleEditMode() {
        this.editModeChange.emit();
    }

    /**
     * pass the answer post to be deleted
     */
    deleteAnswerPost(): void {
        this.interactAnswerPost.emit({
            name: AnswerPostActionName.DELETE,
            answerPost: this.answerPost,
        });
    }

    approveAnswer() {
        this.interactAnswerPost.emit({
            name: AnswerPostActionName.APPROVE,
            answerPost: this.answerPost,
        });
    }
}
