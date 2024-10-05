import { Component, Input, OnChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Post } from 'app/entities/metis/post.model';
import { PostingHeaderDirective } from 'app/shared/metis/posting-header/posting-header.directive';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { faCheckSquare, faCog, faPencilAlt } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { CachingStrategy } from 'app/shared/image/secured-image.component';

@Component({
    selector: 'jhi-post-header',
    templateUrl: './post-header.component.html',
    styleUrls: ['../../metis.component.scss'],
})
export class PostHeaderComponent extends PostingHeaderDirective<Post> implements OnInit, OnDestroy, OnChanges {
    @Input() readOnlyMode = false;
    @Input() lastReadDate?: dayjs.Dayjs;
    @Input() previewMode: boolean;
    @ViewChild(PostCreateEditModalComponent) postCreateEditModal?: PostCreateEditModalComponent;
    isAtLeastInstructorInCourse: boolean;
    mayEditOrDelete = false;

    // Icons
    faPencilAlt = faPencilAlt;
    faCheckSquare = faCheckSquare;
    faCog = faCog;

    ngOnInit() {
        super.ngOnInit();
        this.setMayEditOrDelete();
    }

    /**
     * on changes: re-evaluates authority roles
     */
    ngOnChanges() {
        this.setUserProperties();
        this.setMayEditOrDelete();
        this.setUserAuthorityIconAndTooltip();
    }

    /**
     * on leaving the page, the modal should be closed
     */
    ngOnDestroy(): void {
        this.postCreateEditModal?.modalRef?.close();
    }

    /**
     * invokes the metis service to delete a post
     */
    deletePosting(): void {
        this.metisService.deletePost(this.posting);
    }

    setMayEditOrDelete(): void {
        this.isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
        const isCourseWideChannel = getAsChannelDTO(this.posting.conversation)?.isCourseWide ?? false;
        const mayEditOrDeleteOtherUsersAnswer =
            (isCourseWideChannel && this.isAtLeastInstructorInCourse) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);
        this.mayEditOrDelete = !this.readOnlyMode && !this.previewMode && (this.isAuthorOfPosting || mayEditOrDeleteOtherUsersAnswer);
    }

    protected readonly CachingStrategy = CachingStrategy;
}
