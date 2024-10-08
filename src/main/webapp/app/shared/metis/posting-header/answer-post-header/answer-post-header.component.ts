import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { faCheck, faCog, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

@Component({
    selector: 'jhi-answer-post-header',
    templateUrl: './answer-post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class AnswerPostHeaderComponent extends PostingHeaderDirective<AnswerPost> implements OnInit, OnChanges {
    @Input() isReadOnlyMode = false;
    @Input() lastReadDate?: dayjs.Dayjs;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();

    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;
    mayEditOrDelete = false;

    // Icons
    faCheck = faCheck;
    faPencilAlt = faPencilAlt;
    faCog = faCog;

    ngOnInit() {
        super.ngOnInit();
        this.setMayEditOrDelete();
    }

    ngOnChanges() {
        this.setUserProperties();
        this.setMayEditOrDelete();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * invokes the metis service to delete an answer post
     */
    deletePosting(): void {
        this.metisService.deleteAnswerPost(this.posting);
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
}
