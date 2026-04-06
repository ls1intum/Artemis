import { Component, OnDestroy, OnInit, ViewEncapsulation, effect, inject, input, output, untracked } from '@angular/core';
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
import { deepClone } from 'app/shared/util/deep-clone.util';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageReplyInlineInputComponent extends PostingCreateEditDirective<AnswerPost> implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private draftService = inject(DraftService);

    private readonly DRAFT_KEY_PREFIX = 'thread_draft_';
    private currentUserId: number | undefined;
    private draftMessageSubscription?: Subscription;
    private previousPostingPostId: number | undefined;

    readonly activeConversation = input<ConversationDTO>();

    readonly valueChange = output<void>();

    constructor() {
        super();
        // Track activeConversation changes to reload drafts when switching conversations
        effect(() => {
            this.activeConversation();
            untracked(() => this.loadDraft());
        });
    }

    ngOnInit(): void {
        super.ngOnInit();
        void this.loadCurrentUser();
    }

    protected override onPostingChanged(): void {
        const posting = this.posting();
        const previousPostingPostId = this.previousPostingPostId;
        this.previousPostingPostId = posting?.post?.id;

        // Preserve current form content when re-rendering the same post
        const preservedContent = this.formGroup && posting && previousPostingPostId === posting.post?.id ? this.formGroup.get('content')?.value : undefined;
        super.onPostingChanged();
        if (preservedContent !== undefined) {
            this.formGroup.get('content')?.setValue(preservedContent);
        }
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
        const posting = this.posting();
        if (!posting) {
            return;
        }

        const formContent = content !== undefined ? content : posting.content;

        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [formContent, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
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
        const posting = this.posting();
        if (!posting) {
            this.isLoading = false;
            return;
        }
        const payload = deepClone(posting);
        payload.content = this.formGroup.get('content')?.value;
        this.metisService.createAnswerPost(payload).subscribe({
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
        const posting = this.posting();
        if (!posting) {
            this.isLoading = false;
            return;
        }
        const payload = deepClone(posting);
        payload.content = this.formGroup.get('content')?.value;
        this.metisService.updateAnswerPost(payload).subscribe({
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
        const postId = this.posting()?.post?.id;
        if (!userId || !conversationId || !postId) {
            return '';
        }
        return `${this.DRAFT_KEY_PREFIX}${userId}_${conversationId}_${postId}`;
    }

    private saveDraft(content: string): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        this.draftService.saveDraft(key, content);
    }

    private loadDraft(): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        const draft = this.draftService.loadDraft(key);
        if (draft && this.posting() && this.formGroup) {
            this.formGroup.get('content')?.setValue(draft);
        }
    }

    private clearDraft(): void {
        const key = this.getDraftKey();
        if (!key) {
            return;
        }
        this.draftService.clearDraft(key);
    }

    ngOnDestroy(): void {
        this.draftMessageSubscription?.unsubscribe();
    }
}
