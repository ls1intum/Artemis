import { Component, EventEmitter, Input, OnChanges, OnInit, Output, output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.directive';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { faCheck, faPencilAlt, faSmile } from '@fortawesome/free-solid-svg-icons';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AsyncPipe, KeyValuePipe, NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';

@Component({
    selector: 'jhi-answer-post-reactions-bar',
    templateUrl: './answer-post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
    imports: [
        NgbTooltip,
        EmojiComponent,
        CdkOverlayOrigin,
        FaIconComponent,
        CdkConnectedOverlay,
        EmojiPickerComponent,
        NgClass,
        ConfirmIconComponent,
        AsyncPipe,
        KeyValuePipe,
        ArtemisTranslatePipe,
        ReactingUsersOnPostingPipe,
    ],
})
export class AnswerPostReactionsBarComponent extends PostingsReactionsBarDirective<AnswerPost> implements OnInit, OnChanges {
    @Input() isReadOnlyMode = false;
    @Input() isEmojiCount = false;
    @Input() isLastAnswer = false;

    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() postingUpdated = new EventEmitter<void>();

    // Icons
    readonly farSmile = faSmile;
    readonly faCheck = faCheck;
    readonly faPencilAlt = faPencilAlt;

    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;
    mayDelete: boolean;
    mayEdit: boolean;

    mayDeleteOutput = output<boolean>();
    mayEditOutput = output<boolean>();

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
        const canDeletePost = this.isAnswerOfAnnouncement ? this.metisService.metisUserIsAtLeastInstructorInCourse() : this.metisService.metisUserIsAtLeastTutorInCourse();
        const mayDeleteOtherUsersAnswer =
            (isCourseWideChannel && canDeletePost) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);
        this.mayDelete = !this.isReadOnlyMode && (this.isAuthorOfPosting || (mayDeleteOtherUsersAnswer && canDeletePost));
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
