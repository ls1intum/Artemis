import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/shared/metis/answer-post/answer-post.service';
import { PostingComponent } from '../posting.component';

export interface AnswerPostAction {
    name: AnswerPostActionName;
    answerPost: AnswerPost;
}

export enum AnswerPostActionName {
    DELETE,
    ADD,
    APPROVE,
}

@Component({
    selector: 'jhi-answer-post',
    templateUrl: './answer-post.component.html',
    styleUrls: ['../../../overview/discussion/discussion.scss'],
})
export class AnswerPostComponent extends PostingComponent<AnswerPost> {
    @Input() answerPost: AnswerPost;
    @Output() interactAnswerPost: EventEmitter<AnswerPostAction> = new EventEmitter<AnswerPostAction>();

    constructor(protected answerPostService: AnswerPostService, protected route: ActivatedRoute) {
        super(answerPostService, route);
    }

    /**
     * Updates the text of the selected answerPost
     */
    saveAnswerPost(): void {
        this.isLoading = true;
        this.answerPost.content = this.content;
        this.answerPostService.update(this.courseId, this.answerPost).subscribe({
            next: () => {
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

    /**
     * Toggles the tutorApproved field for this answerPost
     */
    toggleAnswerPostTutorApproved(): void {
        this.answerPost.tutorApproved = !this.answerPost.tutorApproved;
        this.answerPostService.update(this.courseId, this.answerPost).subscribe(() => {
            this.interactAnswerPost.emit({
                name: AnswerPostActionName.APPROVE,
                answerPost: this.answerPost,
            });
        });
    }

    /**
     * toggles the edit Mode
     * set the editor text to the answer text
     */
    toggleEditMode(): void {
        this.isEditMode = !this.isEditMode;
        this.content = this.answerPost.content;
    }

    onInteractAnswerPost($event: AnswerPostAction) {
        this.interactAnswerPost.emit($event);
    }
}
