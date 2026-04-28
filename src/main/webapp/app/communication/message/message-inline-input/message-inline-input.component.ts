import { Component, OnDestroy, OnInit, ViewEncapsulation, effect, inject, input, untracked } from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Post } from 'app/communication/shared/entities/post.model';
import { PostContentValidationPattern } from 'app/communication/metis.util';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AccountService } from 'app/core/auth/account.service';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { Subscription } from 'rxjs';
import { deepClone } from 'app/shared/util/deep-clone.util';

@Component({
    selector: 'jhi-message-inline-input',
    templateUrl: './message-inline-input.component.html',
    styleUrls: ['./message-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageInlineInputComponent extends PostingCreateEditDirective<Post | AnswerPost> implements OnInit, OnDestroy {
    private accountService = inject(AccountService);
    private draftService = inject(DraftService);

    course = input<Course>();
    activeConversation = input<ConversationDTO>();

    private readonly DRAFT_KEY_PREFIX = 'message_draft_';
    private currentUserId: number | undefined;
    private draftMessageSubscription?: Subscription;

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
        this.loadCurrentUser();
    }

    protected override onPostingChanged(): void {
        super.onPostingChanged();
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
    resetFormGroup(): void {
        this.draftMessageSubscription?.unsubscribe();

        this.formGroup = this.formBuilder.group({
            // the pattern ensures that the content must include at least one non-whitespace character
            content: [this.posting()?.content, [Validators.required, Validators.maxLength(this.maxContentLength), PostContentValidationPattern]],
        });

        // Subscribe to content changes to save drafts
        this.draftMessageSubscription = this.formGroup.get('content')?.valueChanges.subscribe((content) => {
            if (content && content.trim()) {
                this.saveDraft(content);
            } else {
                this.clearDraft();
            }
        });
    }

    /**
     * invokes the metis service after setting the title and current date as creation date of the new answer post,
     * ends the process successfully by closing the modal and stopping the button's loading animation
     */
    createPosting(): void {
        // Wait for the markdown editor's 200ms textChangedEmitDelay to complete
        setTimeout(() => {
            const posting = this.posting();
            if (!posting) {
                this.isLoading = false;
                return;
            }
            const payload = deepClone(posting);
            payload.content = this.formGroup.get('content')?.value;
            this.metisService.createPost(payload).subscribe({
                next: (post: Post) => {
                    this.isLoading = false;
                    this.clearDraft();
                    this.onCreate.emit(post);
                },
                error: () => {
                    this.isLoading = false;
                },
            });
        }, 250);
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
        this.isModalOpen.emit();
        this.metisService.updatePost(payload).subscribe({
            next: () => {
                this.isLoading = false;
                this.clearDraft();
            },
            error: () => {
                this.isLoading = false;
            },
        });
    }

    private getDraftKey(): string {
        const userId = this.currentUserId;
        const conversationId = this.activeConversation()?.id;

        if (!conversationId || !userId) {
            return '';
        }
        return `${this.DRAFT_KEY_PREFIX}${userId}_${conversationId}`;
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
