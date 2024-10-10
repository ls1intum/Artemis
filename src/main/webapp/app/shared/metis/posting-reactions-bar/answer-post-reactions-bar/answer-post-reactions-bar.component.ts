import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { faPencilAlt, faSmile } from '@fortawesome/free-solid-svg-icons';
import { MetisService } from 'app/shared/metis/metis.service';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-answer-post-reactions-bar',
    templateUrl: './answer-post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class AnswerPostReactionsBarComponent extends PostingsReactionsBarDirective<AnswerPost> implements OnInit, OnChanges {
    @Input()
    isReadOnlyMode = false;

    @Input() isLastAnswer = false;
    // Icons
    readonly farSmile = faSmile;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;
    mayEditOrDelete = false;
    readonly faPencilAlt = faPencilAlt;
    @Input() isEmojiCount: boolean = false;
    @Output() postingUpdated = new EventEmitter<void>();

    constructor(metisService: MetisService) {
        super(metisService);
    }

    ngOnInit() {
        super.ngOnInit();
        this.setMayEditOrDelete();
    }

    ngOnChanges() {
        super.ngOnChanges();
        this.setMayEditOrDelete();
    }

    isAnyReactionCountAboveZero(): boolean {
        return Object.values(this.reactionMetaDataMap).some((reaction) => reaction.count >= 1);
    }

    deletePosting(): void {
        this.metisService.deleteAnswerPost(this.posting);
    }

    /**
     * builds and returns a Reaction model out of an emojiId and thereby sets the answerPost property properly
     * @param emojiId emojiId to build the model for
     */
    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        reaction.answerPost = this.posting;
        return reaction;
    }

    setMayEditOrDelete(): void {
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthorOfPosting(this.posting.post!);
        this.isAnswerOfAnnouncement = getAsChannelDTO(this.posting.post?.conversation)?.isAnnouncementChannel ?? false;
        const isCourseWideChannel = getAsChannelDTO(this.posting.post?.conversation)?.isCourseWide ?? false;
        const isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
        const mayEditOrDeleteOtherUsersAnswer =
            (isCourseWideChannel && isAtLeastInstructorInCourse) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);
        this.mayEditOrDelete = !this.isReadOnlyMode && (this.isAuthorOfPosting || mayEditOrDeleteOtherUsersAnswer);
    }

    onEditPosting() {
        this.openPostingCreateEditModal.emit();
    }
}
