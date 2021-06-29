import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/overview/postings/answer-post/answer-post.service';

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
    styleUrls: ['../postings.scss'],
})
export class AnswerPostComponent implements OnInit {
    @Input() answerPost: AnswerPost;
    @Input() user: User;
    @Input() isAtLeastTutorInCourse: boolean;
    @Output() interactAnswerPost: EventEmitter<AnswerPostAction> = new EventEmitter<AnswerPostAction>();
    content?: string;
    isLoading = false;
    isEditMode: boolean;
    courseId: number;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p', 'blockquote', 'pre', 'code', 'span', 'li', 'ul', 'ol'];
    allowedHtmlAttributes: string[] = ['href', 'class', 'id'];

    constructor(private answerPostService: AnswerPostService, private route: ActivatedRoute) {}

    /**
     * Sets the text of the answerPost as the editor text
     */
    ngOnInit(): void {
        this.content = this.answerPost.content;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
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
