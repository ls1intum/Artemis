import { Component, ViewEncapsulation } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { FormBuilder, Validators } from '@angular/forms';
import { MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { Post } from 'app/entities/metis/post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PostingCreateEditModalDirective } from 'app/shared/metis/posting-create-edit-modal/posting-create-edit-modal.directive';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-message-inline-input',
    templateUrl: './message-inline-input.component.html',
    styleUrls: ['./message-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MessageInlineInputComponent extends PostingCreateEditModalDirective<Post | AnswerPost> {
    editorHeight = MarkdownEditorHeight.INLINE;

    constructor(protected metisService: MetisService, protected modalService: NgbModal, protected formBuilder: FormBuilder) {
        super(metisService, modalService, formBuilder);
    }

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
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.createPost(this.posting).subscribe({
            next: (post: Post) => {
                this.isLoading = false;
                this.onCreate.emit(post);
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    updateModalTitle(): void {}

    updatePosting(): void {
        this.posting.content = this.formGroup.get('content')?.value;
        this.metisService.updatePost(this.posting).subscribe({
            next: () => {
                this.isLoading = false;
                this.isModalOpen.emit();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    open(): void {}
}
