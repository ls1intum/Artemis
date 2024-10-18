import { Component, OnInit, ViewEncapsulation, inject } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Validators } from '@angular/forms';
import { Post } from 'app/entities/metis/post.model';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';
import { PostingCreateEditDirective } from 'app/shared/metis/posting-create-edit.directive';
import { LocalStorageService } from 'ngx-webstorage';

@Component({
    selector: 'jhi-message-inline-input',
    templateUrl: './message-inline-input.component.html',
    styleUrls: ['./message-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class MessageInlineInputComponent extends PostingCreateEditDirective<Post | AnswerPost> implements OnInit {
    protected localStorageService = inject(LocalStorageService);

    warningDismissed = false;

    ngOnInit(): void {
        super.ngOnInit();
        this.warningDismissed = !!this.localStorageService.retrieve('chatWarningDismissed');
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

    /**
     * invokes the metis service with the updated answer post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
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

    closeAlert() {
        this.warningDismissed = true;
        this.localStorageService.store('chatWarningDismissed', true);
    }
}
