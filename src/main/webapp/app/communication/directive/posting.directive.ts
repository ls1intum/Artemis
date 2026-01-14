import { Posting } from 'app/communication/shared/entities/posting.model';
import { ChangeDetectorRef, Directive, Input, OnDestroy, OnInit, inject } from '@angular/core';
import { MetisService } from 'app/communication/service/metis.service';
import { DisplayPriority } from 'app/communication/metis.util';
import { faBookmark } from '@fortawesome/free-solid-svg-icons';
import { faBookmark as farBookmark } from '@fortawesome/free-regular-svg-icons';
import { isMessagingEnabled } from 'app/core/course/shared/entities/course.model';
import { OneToOneChatService } from 'app/communication/conversations/service/one-to-one-chat.service';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { Router } from '@angular/router';

@Directive()
export abstract class PostingDirective<T extends Posting> implements OnInit, OnDestroy {
    @Input() posting: T;
    @Input() isCommunicationPage: boolean;
    @Input() showChannelReference?: boolean;

    @Input() hasChannelModerationRights = false;
    @Input() isThreadSidebar: boolean;
    abstract get reactionsBar(): any;
    showDropdown = false;
    dropdownPosition = { x: 0, y: 0 };
    showReactionSelector = false;
    clickPosition = { x: 0, y: 0 };

    isAnswerPost = false;
    isDeleted = false;
    readonly timeToDeleteInSeconds = 6;
    deleteTimerInSeconds = 6;
    deleteTimer: NodeJS.Timeout | undefined;
    deleteInterval: NodeJS.Timeout | undefined;

    content?: string;

    protected oneToOneChatService = inject(OneToOneChatService);
    protected metisConversationService = inject(MetisConversationService);
    protected metisService = inject(MetisService);
    protected changeDetector = inject(ChangeDetectorRef);
    protected router = inject(Router);

    // Icons
    farBookmark = farBookmark;
    faBookmark = faBookmark;

    ngOnInit(): void {
        this.content = this.posting.content;
    }

    ngOnDestroy(): void {
        if (this.deleteTimer !== undefined) {
            clearTimeout(this.deleteTimer);
            // Only delete if still marked as deleted
            if (this.isDeleted) {
                this.deletePostingWithoutTimeout();
            }
        }

        if (this.deleteInterval !== undefined) {
            clearInterval(this.deleteInterval);
        }
    }

    onDeleteEvent(isDelete: boolean) {
        this.isDeleted = isDelete;

        if (this.deleteTimer !== undefined) {
            clearTimeout(this.deleteTimer);
        }

        if (this.deleteInterval !== undefined) {
            clearInterval(this.deleteInterval);
        }

        if (isDelete) {
            this.deleteTimerInSeconds = this.timeToDeleteInSeconds;

            this.deleteTimer = setTimeout(
                () => {
                    this.deletePostingWithoutTimeout();
                },
                // We add a tiny buffer to make it possible for the user to react a bit longer than the ui displays (+1000)
                this.deleteTimerInSeconds * 1000 + 1000,
            );

            this.deleteInterval = setInterval(() => {
                this.deleteTimerInSeconds = Math.max(0, this.deleteTimerInSeconds - 1);
                this.changeDetector.detectChanges();
            }, 1000);
        }
    }

    /**
     * Invokes the edit functionality from the child reaction bar.
     * Closes the dropdown afterward.
     */
    editPosting() {
        this.reactionsBar.editPosting();
        this.showDropdown = false;
    }

    /**
     * Invokes the pin toggle from the child reaction bar.
     * Closes dropdown and updates the view.
     */
    togglePin() {
        this.reactionsBar.togglePin();
        this.showDropdown = false;
        this.changeDetector.detectChanges();
    }

    /**
     * Deletes the post by invoking the delete method from the reaction bar.
     */
    deletePost() {
        this.reactionsBar.deletePosting();
        this.showDropdown = false;
    }

    /**
     * Initiates the forward message logic from the reaction bar.
     */
    forwardMessage() {
        this.reactionsBar.forwardMessage();
    }

    /**
     * Delegates pin status retrieval to the reaction bar (used for dropdown display).
     */
    checkIfPinned(): DisplayPriority {
        return this.reactionsBar.checkIfPinned();
    }

    selectReaction(event: any) {
        this.reactionsBar.selectReaction(event);
        this.showReactionSelector = false;
    }

    addReaction(event: MouseEvent) {
        event.preventDefault();
        this.showDropdown = false;

        this.clickPosition = {
            x: event.clientX,
            y: event.clientY,
        };

        this.showReactionSelector = true;
    }

    /**
     * Toggles visibility of the emoji picker (used by emoji button in dropdown).
     */
    toggleEmojiSelect() {
        this.showReactionSelector = !this.showReactionSelector;
    }

    protected toggleSavePost() {
        if (this.posting.isSaved) {
            this.metisService.removeSavedPost(this.posting);
            this.posting.isSaved = false;
        } else {
            this.metisService.savePost(this.posting);
            this.posting.isSaved = true;
        }
    }

    private deletePostingWithoutTimeout() {
        if (this.isAnswerPost) {
            this.metisService.deleteAnswerPost(this.posting);
        } else {
            this.metisService.deletePost(this.posting);
        }
    }

    /**
     * Create a or navigate to one-to-one chat with the referenced user
     *
     * @param referencedUserLogin login of the referenced user
     */
    onUserReferenceClicked(referencedUserLogin: string) {
        const course = this.metisService.getCourse();
        if (isMessagingEnabled(course)) {
            if (this.isCommunicationPage) {
                this.metisConversationService.createOneToOneChat(referencedUserLogin).subscribe();
            } else {
                this.oneToOneChatService.create(course.id!, referencedUserLogin).subscribe((res) => {
                    this.router.navigate(['courses', course.id, 'communication'], {
                        queryParams: {
                            conversationId: res.body!.id,
                        },
                    });
                });
            }
        }
    }

    /**
     * Create a or navigate to one-to-one chat with the referenced user
     */
    onUserNameClicked() {
        if (!this.posting.author?.id) {
            return;
        }

        const referencedUserId = this.posting.author?.id;

        const course = this.metisService.getCourse();
        if (isMessagingEnabled(course)) {
            if (this.isCommunicationPage) {
                this.metisConversationService.createOneToOneChatWithId(referencedUserId).subscribe();
            } else {
                this.oneToOneChatService.createWithId(course.id!, referencedUserId).subscribe((res) => {
                    this.router.navigate(['courses', course.id, 'communication'], {
                        queryParams: {
                            conversationId: res.body!.id,
                        },
                    });
                });
            }
        }
    }
}
