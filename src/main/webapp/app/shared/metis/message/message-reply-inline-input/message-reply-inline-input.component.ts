import { Component, EventEmitter, OnChanges, OnInit, Output, SimpleChanges, ViewEncapsulation, inject, input } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MetisService } from 'app/shared/metis/metis.service';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';
import { PostingCreateEditDirective } from 'app/shared/metis/posting-create-edit.directive';
import { LocalStorageService } from 'ngx-webstorage';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    standalone: true,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, TranslateDirective, PostingButtonComponent, ArtemisSharedCommonModule],
})
export class MessageReplyInlineInputComponent extends PostingCreateEditDirective<AnswerPost> implements OnInit, OnChanges {
    protected metisService = inject(MetisService);
    protected modalService = inject(NgbModal);
    protected formBuilder = inject(FormBuilder);
    protected localStorageService = inject(LocalStorageService);

    warningDismissed = false;

    readonly activeConversation = input<ConversationDTO>();

    @Output()
    valueChange = new EventEmitter<void>();

    ngOnInit(): void {
        super.ngOnInit();
        this.warningDismissed = !!this.localStorageService.retrieve('chatWarningDismissed');
    }

    ngOnChanges(changes: SimpleChanges | void) {
        if (this.formGroup && changes) {
            for (const propName in changes) {
                if (changes.hasOwnProperty(propName) && propName === 'posting') {
                    if (changes['posting'].previousValue?.post?.id === changes['posting'].currentValue?.post?.id) {
                        this.posting.content = this.formGroup.get('content')?.value;
                    }
                }
            }
        }

        super.ngOnChanges();
    }

    /**
     * resets the answer post content
     */
    resetFormGroup(content: string | undefined = undefined): void {
        if (content !== undefined) {
            this.posting.content = content;
        }

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
                this.resetFormGroup('');
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

    closeAlert() {
        this.warningDismissed = true;
        this.localStorageService.store('chatWarningDismissed', true);
    }
}
