import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { faSmile } from '@fortawesome/free-regular-svg-icons';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs/esm';
import { isChannelDto } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDto } from 'app/entities/metis/conversation/group-chat.model';
import { AccountService } from 'app/core/auth/account.service';
import { isOneToOneChatDto } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { ConversationDto } from 'app/entities/metis/conversation/conversation.model';

@Component({
    selector: 'jhi-post-reactions-bar',
    templateUrl: './post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class PostReactionsBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges {
    pinTooltip: string;
    archiveTooltip: string;
    displayPriority: DisplayPriority;
    canPin = false;
    readonly DisplayPriority = DisplayPriority;

    // Icons
    farSmile = faSmile;

    @Input()
    readOnlyMode = false;
    @Input() showAnswers: boolean;
    @Input() sortedAnswerPosts: AnswerPost[];
    @Input() isCourseMessagesPage: boolean;
    @Input() lastReadDate?: dayjs.Dayjs;

    @Output() showAnswersChange = new EventEmitter<boolean>();
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() openThread = new EventEmitter<void>();

    constructor(
        metisService: MetisService,
        private accountService: AccountService,
    ) {
        super(metisService);
    }

    /**
     * on initialization: call resetTooltipsAndPriority
     */
    ngOnInit() {
        super.ngOnInit();
        this.resetTooltipsAndPriority();

        const currentConversation = this.metisService.getCurrentConversation();
        this.setCanPin(currentConversation);
    }

    /**
     * Checks whether the user can pin the message in the conversation
     *
     * @param currentConversation the conversation the post belongs to
     */
    private setCanPin(currentConversation: ConversationDto | undefined) {
        if (!currentConversation) {
            this.canPin = false;
            return;
        }

        if (isChannelDto(currentConversation)) {
            this.canPin = currentConversation.hasChannelModerationRights ?? false;
        } else if (isGroupChatDto(currentConversation)) {
            this.canPin = currentConversation.creator?.id === this.accountService.userIdentity?.id;
        } else if (isOneToOneChatDto(currentConversation)) {
            this.canPin = true;
        }
    }

    /**
     * on changes: call resetTooltipsAndPriority
     */
    ngOnChanges() {
        super.ngOnChanges();
        this.resetTooltipsAndPriority();
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

    /**
     * provides the tooltip for the pin icon dependent on the user authority and the pin state of a posting
     *
     */
    getPinTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.displayPriority === DisplayPriority.PINNED) {
            return 'artemisApp.metis.removePinPostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && this.displayPriority !== DisplayPriority.PINNED) {
            return 'artemisApp.metis.pinPostTutorTooltip';
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

    /**
     * provides the tooltip for the archive icon dependent on the user authority and the archive state of a posting
     */
    getArchiveTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.displayPriority === DisplayPriority.ARCHIVED) {
            return 'artemisApp.metis.removeArchivePostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && this.displayPriority !== DisplayPriority.ARCHIVED) {
            return 'artemisApp.metis.archivePostTutorTooltip';
        }
        return 'artemisApp.metis.archivedPostTooltip';
    }

    private resetTooltipsAndPriority() {
        this.displayPriority = this.posting.displayPriority!;
        this.pinTooltip = this.getPinTooltip();
        this.archiveTooltip = this.getArchiveTooltip();
    }
}
