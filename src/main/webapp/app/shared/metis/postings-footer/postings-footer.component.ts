import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { User } from 'app/core/user/user.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { AnswerPostAction, AnswerPostActionName } from 'app/shared/metis/answer-post/answer-post.component';

@Component({
    selector: 'jhi-posting-footer',
    templateUrl: './postings-footer.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class PostingsFooterComponent implements OnInit {
    @Input() answerPost: AnswerPost;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Input() courseId: number;
    @Output() editModeChange: EventEmitter<void> = new EventEmitter();
    @Output() interactAnswerPost: EventEmitter<AnswerPostAction> = new EventEmitter<AnswerPostAction>();
    isAuthorOfAnswerPost: boolean;

    constructor(private answerPostService: AnswerPostService) {}

    ngOnInit(): void {
        this.isAuthorOfAnswerPost = this.user ? this.answerPost?.author!.id === this.user.id : false;
    }

    approveAnswer() {
        this.interactAnswerPost.emit({
            name: AnswerPostActionName.APPROVE,
            answerPost: this.answerPost,
        });
    }
}
