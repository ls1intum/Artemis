import { Component, EventEmitter, Input, Output, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { AnswerPostService } from 'app/overview/postings/answer-post/answer-post.service';
import { EditorMode } from 'app/shared/markdown-editor/markdown-editor.component';

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
    editText?: string;
    isLoading = false;
    isEditMode: boolean;
    EditorMode = EditorMode;
    courseId: number;

    // Only allow certain html tags and no attributes
    allowedHtmlTags: string[] = ['a', 'b', 'strong', 'i', 'em', 'mark', 'small', 'del', 'ins', 'sub', 'sup', 'p'];
    allowedHtmlAttributes: string[] = ['href'];

    constructor(private answerPostService: AnswerPostService, private route: ActivatedRoute) {}

    /**
     * Sets the text of the answerPost as the editor text
     */
    ngOnInit(): void {
        this.editText = this.answerPost.content;
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
        this.answerPostService.delete(this.courseId, this.answerPost.id!).subscribe(() => {
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
        this.answerPost.content = this.editText;
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
        this.editText = this.answerPost.content;
    }
}
