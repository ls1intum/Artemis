import { Component, EventEmitter, OnChanges, OnDestroy, OnInit, Output, SimpleChanges, ViewEncapsulation, inject, input } from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostContentValidationPattern } from 'app/communication/metis.util';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageReplyInlineInputComponent extends PostingCreateEditDirective<AnswerPost> implements OnInit, OnChanges, OnDestroy {
    private accountService = inject(AccountService);
    private draftService = inject(DraftService);

    private readonly DRAFT_KEY_PREFIX = 'thread_draft_';
    private currentUserId: number | undefined;
    private draftMessageSubscription?: Subscription;

    readonly activeConversation = input<ConversationDTO>();

    @Output() valueChange = new EventEmitter<void>();

    ngOnInit(): void {
        super.ngOnInit();
        void this.loadCurrentUser();
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
        this.loadDraft();
    }

    private async loadCurrentUser(): Promise<void> {
        const account = await this.accountService.identity();
        if (account?.id) {
            this.currentUserId = account.id;
            this.loadDraft();
        }
    }

    /**
     * resets the answer post content
     */
    resetFormGroup(content: string | undefined = undefined): void {
        this.draftMessageSubscription?.unsubscribe();

        if (content !== undefined) {
            this.posting.content = content;
        }

        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [this.posting.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
        });

        // Subscribe and store the subscription
        this.draftMessageSubscription = this.formGroup.get('content')?.valueChanges.subscribe((content) => {
            if (content && content.trim()) {
                this.saveDraft(content);
            } else {
                this.clearDraft();
            }
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
                this.clearDraft();
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
                this.clearDraft();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    /**
     * Generates a unique key for storing draft messages based on user ID, conversation ID, and post ID.
     * Returns an empty string if any required IDs are missing.
     *
     * @returns A unique draft key string or empty string if required IDs are missing
     */
    private getDraftKey(): string {
        const userId = this.currentUserId;
        const conversationId = this.activeConversation()?.id;
        const postId = this.posting.post?.id;
        if (!userId || !conversationId || !postId) {
            return '';
        }
        return `${this.DRAFT_KEY_PREFIX}${userId}_${conversationId}_${postId}`;
    }

    private saveDraft(content: string): void {
        const key = this.getDraftKey();
        this.draftService.saveDraft(key, content);
    }

    private loadDraft(): void {
        const key = this.getDraftKey();
        const draft = this.draftService.loadDraft(key);
        if (draft) {
            this.posting.content = draft;
            this.resetFormGroup();
        }
    }

    private clearDraft(): void {
        const key = this.getDraftKey();
        this.draftService.clearDraft(key);
    }

    ngOnDestroy(): void {
        this.draftMessageSubscription?.unsubscribe();
    }
}
