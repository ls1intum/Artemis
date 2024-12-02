import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output, ViewChild, output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.directive';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { faTrashAlt } from '@fortawesome/free-regular-svg-icons';
import { faArrowRight, faPencilAlt, faSmile } from '@fortawesome/free-solid-svg-icons';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';
import { getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { AccountService } from 'app/core/auth/account.service';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';

@Component({
    selector: 'jhi-post-reactions-bar',
    templateUrl: './post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class PostReactionsBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges, OnDestroy {
    pinTooltip: string;
    displayPriority: DisplayPriority;
    canPin = false;
    readonly DisplayPriority = DisplayPriority;

    // Icons
    faSmile = faSmile;
    faArrowRight = faArrowRight;
    faPencilAlt = faPencilAlt;
    faTrash = faTrashAlt;

    @Input()
    readOnlyMode = false;
    @Input() showAnswers: boolean;
    @Input() sortedAnswerPosts: AnswerPost[];
    @Input() isCommunicationPage: boolean;
    @Input() lastReadDate?: dayjs.Dayjs;

    @Output() showAnswersChange = new EventEmitter<boolean>();
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() closePostingCreateEditModal = new EventEmitter<void>();
    @Output() openThread = new EventEmitter<void>();
    @Input() previewMode: boolean;
    isAtLeastInstructorInCourse: boolean;
    mayDeleteOutput = output<boolean>();
    mayEditOutput = output<boolean>();
    @Output() canPinOutput = new EventEmitter<boolean>();
    mayEdit: boolean;
    mayDelete: boolean;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    @Input() isEmojiCount = false;
    @Input() hoverBar: boolean = true;
    @ViewChild('createEditModal') createEditModal!: PostCreateEditModalComponent;

    constructor(
        metisService: MetisService,
        private accountService: AccountService,
    ) {
        super(metisService);
    }

    isAnyReactionCountAboveZero(): boolean {
        return Object.values(this.reactionMetaDataMap).some((reaction) => reaction.count >= 1);
    }

    openAnswerView() {
        this.showAnswersChange.emit(true);
        this.openPostingCreateEditModal.emit();
    }

    closeAnswerView() {
        this.showAnswersChange.emit(false);
        this.closePostingCreateEditModal.emit();
    }

    /**
     * on initialization: call resetTooltipsAndPriority
     */
    ngOnInit() {
        super.ngOnInit();

        const currentConversation = this.metisService.getCurrentConversation();
        this.setCanPin(currentConversation);
        this.setMayDelete();
        this.setMayEdit();
        this.resetTooltipsAndPriority();
    }

    ngOnDestroy() {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * Checks whether the user can pin the message in the conversation
     *
     * @param currentConversation the conversation the post belongs to
     */
    private setCanPin(currentConversation: ConversationDTO | undefined) {
        if (!currentConversation) {
            this.canPin = this.metisService.metisUserIsAtLeastInstructorInCourse();
            return;
        }

        if (isChannelDTO(currentConversation)) {
            this.canPin = currentConversation.hasChannelModerationRights ?? false;
        } else if (isGroupChatDTO(currentConversation)) {
            this.canPin = currentConversation.creator?.id === this.accountService.userIdentity?.id;
        } else if (isOneToOneChatDTO(currentConversation)) {
            this.canPin = true;
        }
        this.canPinOutput.emit(this.canPin);
    }

    /**
     * on changes: call resetTooltipsAndPriority
     */
    ngOnChanges() {
        super.ngOnChanges();
        this.resetTooltipsAndPriority();
        this.setMayDelete();
        this.setMayEdit();
    }

    /**
     * builds and returns a Reaction model out of an emojiId and thereby sets the post property properly
     * @param emojiId emojiId to build the model for
     */
    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        reaction.post = this.posting;
        return reaction;
    }

    /**
     * changes the state of the displayPriority property on a post to PINNED by invoking the metis service
     * in case the displayPriority is already set to PINNED, it will be changed to NONE
     */
    togglePin() {
        if (this.displayPriority === DisplayPriority.PINNED) {
            this.displayPriority = DisplayPriority.NONE;
        } else {
            this.displayPriority = DisplayPriority.PINNED;
        }
        this.posting.displayPriority = this.displayPriority;
        this.metisService.updatePostDisplayPriority(this.posting.id!, this.displayPriority).subscribe();
    }

    checkIfPinned(): DisplayPriority {
        return this.displayPriority;
    }

    /**
     * provides the tooltip for the pin icon dependent on the user authority and the pin state of a posting
     *
     */
    getPinTooltip(): string {
        if (this.canPin && this.displayPriority === DisplayPriority.PINNED) {
            return 'artemisApp.metis.removePinPostTooltip';
        }
        if (this.canPin && this.displayPriority !== DisplayPriority.PINNED) {
            return 'artemisApp.metis.pinPostTooltip';
        }
        return 'artemisApp.metis.pinnedPostTooltip';
    }

    getShowNewMessageIcon(): boolean {
        let showIcon = false;
        // iterate over all answer posts
        this.sortedAnswerPosts.forEach((answerPost) => {
            // check if the answer post is newer than the last read date
            const isAuthor = this.metisService.metisUserIsAuthorOfPosting(answerPost);
            if (!isAuthor && !!(!this.lastReadDate || (this.lastReadDate && answerPost.creationDate && answerPost.creationDate.isAfter(this.lastReadDate!)))) {
                showIcon = true;
            }
        });
        return showIcon;
    }

    private resetTooltipsAndPriority() {
        this.displayPriority = this.posting.displayPriority!;
        this.pinTooltip = this.getPinTooltip();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.isDeleteEvent.emit(true);
    }

    editPosting() {
        if (this.posting.title != '') {
            this.createEditModal.open();
        } else {
            this.isModalOpen.emit();
        }
    }

    setMayDelete(): void {
        this.isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
        const isCourseWideChannel = getAsChannelDTO(this.posting.conversation)?.isCourseWide ?? false;
        const mayDeleteOtherUsersAnswer =
            (isCourseWideChannel && this.isAtLeastInstructorInCourse) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);
        this.mayDelete = !this.readOnlyMode && !this.previewMode && (this.isAuthorOfPosting || mayDeleteOtherUsersAnswer);
        this.mayDeleteOutput.emit(this.mayDelete);
    }

    setMayEdit(): void {
        this.mayEdit = this.isAuthorOfPosting;
        this.mayEditOutput.emit(this.mayEdit);
    }
}
