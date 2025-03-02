import { Component, EventEmitter, OnChanges, OnInit, Output, SimpleChanges, ViewEncapsulation, inject, input, signal } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostContentValidationPattern } from 'app/shared/metis/metis.util';
import { PostingButtonComponent } from 'app/shared/metis/posting-button/posting-button.component';
import { PostingCreateEditDirective } from 'app/shared/metis/posting-create-edit.directive';
import { PostingMarkdownEditorComponent } from 'app/shared/metis/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'ngx-webstorage';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { Post } from 'app/entities/metis/post.model';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, TranslateDirective, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageReplyInlineInputComponent extends PostingCreateEditDirective<AnswerPost> implements OnInit, OnChanges {
    private localStorageService = inject(LocalStorageService);

    warningDismissed = false;

    readonly activeConversation = input<ConversationDTO>();

    @Output() valueChange = new EventEmitter<void>();

    sendAsDirectMessage = signal<boolean>(false);

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

    toggleSendAsDirectMessage(): void {
        this.sendAsDirectMessage.set(!this.sendAsDirectMessage());
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
        this.isLoading = true;

        const createAnswerPost$ = this.metisService.createAnswerPost(this.posting);

        createAnswerPost$.subscribe({
            next: (createdAnswerPost: AnswerPost) => {
                if (this.sendAsDirectMessage()) {
                    const newPost = this.mapAnswerPostToPost(createdAnswerPost);
                    this.metisService.createPost(newPost).subscribe({
                        next: () => this.finalizeCreation(createdAnswerPost),
                        error: () => (this.isLoading = false),
                    });
                } else {
                    this.finalizeCreation(createdAnswerPost);
                }
            },
            error: () => (this.isLoading = false),
        });
    }

    private finalizeCreation(answerPost: AnswerPost): void {
        this.resetFormGroup('');
        this.isLoading = false;
        this.onCreate.emit(answerPost);
    }

    private mapAnswerPostToPost(answerPost: AnswerPost): Post {
        return {
            content: answerPost.content,
            conversation: this.activeConversation(),
            originalAnswerId: answerPost.id,
        } as Post;
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
