import { ChangeDetectorRef, Component, OnChanges, OnDestroy, OnInit, SimpleChanges, ViewEncapsulation, inject, input, output, signal } from '@angular/core';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostContentValidationPattern } from 'app/communication/metis.util';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { PostingCreateEditDirective } from 'app/communication/directive/posting-create-edit.directive';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'ngx-webstorage';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { Post } from 'app/communication/shared/entities/post.model';
import { ChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { AccountService } from 'app/core/auth/account.service';
import { DraftService } from 'app/communication/message/service/draft-message.service';
import { Subscription } from 'rxjs';
import { canCreateNewMessageInConversation } from 'app/communication/conversations/conversation-permissions.utils';

@Component({
    selector: 'jhi-message-reply-inline-input',
    templateUrl: './message-reply-inline-input.component.html',
    styleUrls: ['./message-reply-inline-input.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [FormsModule, ReactiveFormsModule, PostingMarkdownEditorComponent, TranslateDirective, PostingButtonComponent, ArtemisTranslatePipe],
})
export class MessageReplyInlineInputComponent extends PostingCreateEditDirective<AnswerPost> implements OnInit, OnChanges, OnDestroy {
    private localStorageService = inject(LocalStorageService);

    private cdr = inject(ChangeDetectorRef);

    warningDismissed = false;
    channelName?: string;
    private accountService = inject(AccountService);
    private draftService = inject(DraftService);

    private readonly DRAFT_KEY_PREFIX = 'thread_draft_';
    private currentUserId: number | undefined;
    private draftMessageSubscription?: Subscription;

    readonly activeConversation = input<ConversationDTO>();

    valueChange = output<void>();

    sendAsDirectMessage = signal<boolean>(false);

    /**
     * Returns true if the user can send as direct message in the current conversation
     */
    get canSendAsDirectMessage(): boolean {
        const conversation = this.activeConversation();
        if (!conversation) {
            return false;
        }

        // If it's a channel and it's an announcement channel, disable the checkbox
        if (conversation.type === 'channel') {
            const channel = conversation as ChannelDTO;
            if (channel.isAnnouncementChannel) {
                return false;
            }
        }

        return canCreateNewMessageInConversation(conversation as ChannelDTO);
    }

    ngOnInit(): void {
        super.ngOnInit();
        this.warningDismissed = !!this.localStorageService.retrieve('chatWarningDismissed');
        void this.loadCurrentUser();
        this.channelName = (this.activeConversation() as ChannelDTO).name;

        // If it's an announcement channel, ensure the checkbox is unchecked
        if (this.activeConversation()?.type === 'channel') {
            const channel = this.activeConversation() as ChannelDTO;
            if (channel.isAnnouncementChannel) {
                this.sendAsDirectMessage.set(false);
            }
        }

        this.cdr.detectChanges();
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

        // Check if conversation changed and update checkbox state for announcement channels
        if (changes && changes['activeConversation']) {
            if (this.activeConversation()?.type === 'channel') {
                const channel = this.activeConversation() as ChannelDTO;
                if (channel.isAnnouncementChannel) {
                    this.sendAsDirectMessage.set(false);
                }
            }
        }
    }

    private async loadCurrentUser(): Promise<void> {
        const account = await this.accountService.identity();
        if (account?.id) {
            this.currentUserId = account.id;
            this.loadDraft();
        }
    }

    toggleSendAsDirectMessage(): void {
        // Don't allow toggling if it's an announcement channel
        if (!this.canSendAsDirectMessage) {
            return;
        }
        this.sendAsDirectMessage.set(!this.sendAsDirectMessage());
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
        this.isLoading = true;

        const createAnswerPost$ = this.metisService.createAnswerPost(this.posting);

        createAnswerPost$.subscribe({
            next: (createdAnswerPost: AnswerPost) => {
                if (this.sendAsDirectMessage()) {
                    const newPost = this.mapAnswerPostToPost(createdAnswerPost);
                    this.metisService.createPost(newPost).subscribe({
                        next: () => this.finalizeCreation(newPost),
                        error: () => (this.isLoading = false),
                    });
                }
                this.finalizeCreation(createdAnswerPost);
            },
            error: () => (this.isLoading = false),
        });
    }

    private finalizeCreation(posting: Posting): void {
        this.resetFormGroup('');
        this.clearDraft();
        this.isLoading = false;
        this.onCreate.emit(posting);
    }

    private mapAnswerPostToPost(answerPost: AnswerPost): Post {
        if (!answerPost.post) {
            throw new Error('Answer post does not have a reference to its parent post');
        }
        return {
            content: answerPost.content,
            conversation: this.activeConversation(),
            originalPostId: answerPost.post!.id,
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
                this.clearDraft();
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
