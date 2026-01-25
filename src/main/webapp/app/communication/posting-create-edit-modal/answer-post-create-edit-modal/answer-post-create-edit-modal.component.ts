import { Component, ViewContainerRef, ViewEncapsulation, input, output } from '@angular/core';
import { Observable } from 'rxjs';
import { PostingCreateEditModalDirective } from 'app/communication/posting-create-edit-modal/posting-create-edit-modal.directive';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostContentValidationPattern } from 'app/communication/metis.util';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';

@Component({
    selector: 'jhi-answer-post-create-edit-modal',
    templateUrl: './answer-post-create-edit-modal.component.html',
    styleUrls: ['answer-post-create-edit-modal.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent],
})
export class AnswerPostCreateEditModalComponent extends PostingCreateEditModalDirective<AnswerPost> {
    createEditAnswerPostContainerRef = input<ViewContainerRef>();
    postingUpdated = output<Posting>();
    isInputOpen = false;
    createOverride = input<((answerPost: AnswerPost) => Observable<AnswerPost>) | undefined>(undefined);

    /**
     * renders the ng-template to edit or create an answerPost
     */
    open(): void {
        this.close();
        this.createEditAnswerPostContainerRef()?.createEmbeddedView(this.postingEditor);
        this.isInputOpen = true;
    }

    /**
     * clears the container to remove the input field when the user clicks cancel
     */
    close(): void {
        this.createEditAnswerPostContainerRef()?.clear();
        this.resetFormGroup();
        this.isInputOpen = false;
    }

    /**
     * resets the answer post content
     */
    resetFormGroup(): void {
        this.posting = this.posting || { content: '' };
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
        const override = this.createOverride();
        const create$ = override ? override(this.posting) : this.metisService.createAnswerPost(this.posting);

        create$.subscribe({
            next: (answerPost: AnswerPost) => {
                this.resetFormGroup();
                this.isLoading = false;
                this.onCreate.emit(answerPost);
                this.createEditAnswerPostContainerRef()?.clear();
                this.isInputOpen = false;
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
            next: (updatedPost: AnswerPost) => {
                this.postingUpdated.emit(updatedPost);
                this.isLoading = false;
                this.isInputOpen = false;
                this.createEditAnswerPostContainerRef()?.clear();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * updates the title in accordance with the current use case (edit or create)
     */
    updateModalTitle(): void {
        if (this.editType === this.EditType.UPDATE) {
            this.modalTitle = 'artemisApp.metis.editPosting';
        } else if (this.editType === this.EditType.CREATE) {
            this.modalTitle = 'artemisApp.metis.createModalTitleAnswer';
        }
    }
}
