import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingService } from 'app/overview/postings/posting.service';

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
    @Output() interactAnswer = new EventEmitter<AnswerPostAction>();
    answerPostEditContent?: string;
    isLoading = false;
    isEditMode: boolean;
    courseId: number;

    // Only allow certain html tags and attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p', 'ins', 'blockquote', 'pre', 'code', 'span'];
    allowedHtmlAttributes: string[] = ['href', 'class', 'id'];

    constructor(private postingService: PostingService, private route: ActivatedRoute) {}

    /**
     * Sets the text of the answerPost as the editor text
     */
    ngOnInit(): void {
        this.answerPostEditContent = this.answerPost.content;
        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
    }

    /**
     * Takes a answerPost and determines if the user is the author of it
     * @param {AnswerPost} answerPost
     * @returns {boolean}
     */
    isAuthorOfAnswerPost(answerPost: AnswerPost): boolean {
        return this.user ? answerPost.author!.id === this.user.id : false;
    }

    /**
     * Deletes this answerPost
     */
    deleteAnswerPost(): void {
        this.postingService.delete(this.courseId, this.answerPost.id!).subscribe(() => {
            this.interactAnswer.emit({
                name: AnswerPostActionName.DELETE,
                answerPost: this.answerPost,
            });
        });
    }

    /**
     * Updates the text of the selected answerPost
     */
    saveAnswerPost(): void {
        this.isLoading = true;
        this.answerPost.content = this.answerPostEditContent;
        this.postingService.update(this.courseId, this.answerPost).subscribe({
            next: () => {
                this.isEditMode = false;
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
        this.postingService.update(this.courseId, this.answerPost).subscribe(() => {
            this.interactAnswer.emit({
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
        this.answerPostEditContent = this.answerPost.content;
    }
}
