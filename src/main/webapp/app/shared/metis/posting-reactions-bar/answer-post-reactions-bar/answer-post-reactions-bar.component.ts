import { Component, EventEmitter, Input, OnChanges, OnInit, Output, output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { faCheck, faPencilAlt, faSmile } from '@fortawesome/free-solid-svg-icons';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisConfirmIconModule } from 'app/shared/confirm-icon/confirm-icon.module';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';
import { OverlayModule } from '@angular/cdk/overlay';

@Component({
    selector: 'jhi-answer-post-reactions-bar',
    templateUrl: './answer-post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
    standalone: true,
    imports: [ReactingUsersOnPostingPipe, EmojiComponent, ArtemisSharedModule, ArtemisConfirmIconModule, EmojiPickerComponent, OverlayModule],
})
export class AnswerPostReactionsBarComponent extends PostingsReactionsBarDirective<AnswerPost> implements OnInit, OnChanges {
    @Input()
    isReadOnlyMode = false;

    @Input() isLastAnswer = false;
    // Icons
    readonly farSmile = faSmile;
    readonly faCheck = faCheck;

    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;
    mayDeleteOutput = output<boolean>();
    mayDelete: boolean;
    mayEditOutput = output<boolean>();
    mayEdit: boolean;
    readonly faPencilAlt = faPencilAlt;
    @Input() isEmojiCount: boolean = false;
    @Output() postingUpdated = new EventEmitter<void>();

    ngOnInit() {
        super.ngOnInit();
        this.setMayEdit();
        this.setMayDelete();
    }

    ngOnChanges() {
        super.ngOnChanges();
        this.setMayEdit();
        this.setMayDelete();
    }

    isAnyReactionCountAboveZero(): boolean {
        return Object.values(this.reactionMetaDataMap).some((reaction) => reaction.count >= 1);
    }

    /**
     * invokes the metis service to delete an answer post
     */
    deletePosting(): void {
        this.isDeleteEvent.emit(true);
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

    setMayDelete(): void {
        // determines if the current user is the author of the original post, that the answer belongs to
        this.isAuthorOfOriginalPost = this.metisService.metisUserIsAuthorOfPosting(this.posting.post!);
        this.isAnswerOfAnnouncement = getAsChannelDTO(this.posting.post?.conversation)?.isAnnouncementChannel ?? false;
        const isCourseWideChannel = getAsChannelDTO(this.posting.post?.conversation)?.isCourseWide ?? false;
        const isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
        const mayEditOrDeleteOtherUsersAnswer =
            (isCourseWideChannel && isAtLeastInstructorInCourse) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);
        this.mayDelete = !this.isReadOnlyMode && (this.isAuthorOfPosting || mayEditOrDeleteOtherUsersAnswer);
        this.mayDeleteOutput.emit(this.mayDelete);
    }

    setMayEdit() {
        this.mayEdit = this.isAuthorOfPosting;
        this.mayEditOutput.emit(this.mayEdit);
    }

    editPosting() {
        this.openPostingCreateEditModal.emit();
    }

    /**
     * toggles the resolvesPost property of an answer post if the user is at least tutor in a course or the user is the author of the original post,
     * delegates the update to the metis service
     */
    toggleResolvesPost(): void {
        if (this.isAtLeastTutorInCourse || this.isAuthorOfOriginalPost) {
            this.posting.resolvesPost = !this.posting.resolvesPost;
            this.metisService.updateAnswerPost(this.posting).subscribe();
        }
    }
}
