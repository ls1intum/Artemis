import { Component, ViewEncapsulation } from '@angular/core';
import { PostingCreateEditModalDirective } from 'app/shared/metis/posting-create-edit-modal/posting-create-edit-modal.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import { FormBuilder, Validators } from '@angular/forms';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MessageReplyInlineInputComponent extends PostingCreateEditModalDirective<AnswerPost> {
    editorHeight = MarkdownEditorHeight.INLINE;

    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

    /**
     * renders the ng-template to edit or create an answerPost
     */
    open(): void {}

    /**
     * resets the answer post content
     */
    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
        });
    }

    /**
     * invokes the metis service after setting current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.createAnswerPost(this.posting).subscribe({
            next: (answerPost: AnswerPost) => {
                this.resetFormGroup();
                this.isLoading = false;
                this.onCreate.emit(answerPost);
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * invokes the metis service with the updated answer post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.updateAnswerPost(this.posting).subscribe({
            next: () => {
                this.isLoading = false;
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updateModalTitle(): void {}
}
