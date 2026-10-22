import { Component, OnDestroy, OnInit, ViewEncapsulation, effect, inject, input, output, untracked } from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { PostContentValidationPattern } from 'app/communication/communication.util';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { AccountService } from 'app/core/auth/account.service';
import { DraftMessageInputDirective } from '../draft-message-input.directive';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageReplyInlineInputComponent extends DraftMessageInputDirective<AnswerPost> implements OnInit, OnDestroy {
    private accountService = inject(AccountService);

    private readonly DRAFT_KEY_PREFIX = 'thread_draft_';
    private currentUserId: number | undefined;
    private draftMessageSubscription?: Subscription;
    private previousPostingPostId: number | undefined;
    private previousPostingId: number | undefined;

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

    override ngOnInit(): void {
        super.ngOnInit();
        void this.loadCurrentUser();
    }

    protected override onPostingChanged(): void {
        const posting = this.posting();
        const previousPostingPostId = this.previousPostingPostId;
        const previousPostingId = this.previousPostingId;
        this.previousPostingPostId = posting?.post?.id;
        this.previousPostingId = posting?.id;

        // Preserve current form content only when re-rendering the same reply draft.
        const preservedContent =
            this.formGroup && posting && previousPostingPostId === posting.post?.id && previousPostingId === posting.id ? this.formGroup.get('content')?.value : undefined;
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
     * invokes the communication service after setting current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        this.submitPosting(
            (payload) => this.communicationService.createAnswerPost(payload),
            (answerPost: AnswerPost) => {
                this.resetFormGroup('');
                this.isLoading.set(false);
                this.clearDraft();
                this.onCreate.emit(answerPost);
            },
        );
    }

    /**
     * invokes the communication service with the updated answer post
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    updatePosting(): void {
        this.submitPosting(
            (payload) => this.communicationService.updateAnswerPost(payload),
            () => {
                this.isLoading.set(false);
                this.clearDraft();
            },
        );
    }

    /**
     * Generates a unique key for storing draft messages based on user ID, conversation ID, and post ID.
     * Returns an empty string if any required IDs are missing.
     *
     * @returns A unique draft key string or empty string if required IDs are missing
     */
    protected override getDraftKey(): string {
        const userId = this.currentUserId;
        const conversationId = this.activeConversation()?.id;
        const postId = this.posting()?.post?.id;
        if (!userId || !conversationId || !postId) {
            return '';
        }
        return `${this.DRAFT_KEY_PREFIX}${userId}_${conversationId}_${postId}`;
    }

    ngOnDestroy(): void {
        this.draftMessageSubscription?.unsubscribe();
    }
}
