import { Component, OnChanges, OnInit } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/postings-reactions-bar/postings-reactions-bar.component';

@Component({
    selector: 'jhi-post-reactions-bar',
    templateUrl: './post-reactions-bar.component.html',
    styleUrls: ['../postings-reactions-bar.component.scss'],
})
export class PostReactionsBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges {
    pinTooltip: string;
    archiveTooltip: string;

    /**
     * on initialization: sets the required tooltips
     */
    ngOnInit() {
        super.ngOnInit();
        this.pinTooltip = this.getPinTooltip();
        this.archiveTooltip = this.getArchiveTooltip();
    }

    /**
     * on changes: sets the required tooltips
     */
    ngOnChanges() {
        super.ngOnChanges();
        this.pinTooltip = this.getPinTooltip();
        this.archiveTooltip = this.getArchiveTooltip();
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
     * flips the value of the pinned property on a posting by invoking the metis service
     */
    togglePin() {
        this.metisService.updatePostPinState(this.posting, !this.posting.pinned).subscribe(() => {});
    }

    /**
     * flips the value of the archived property on a posting by invoking the metis service
     */
    toggleArchive() {
        this.metisService.updatePostArchiveState(this.posting, !this.posting.archived).subscribe(() => {});
    }

    /**
     * provides the tooltip for the pin icon dependent on the user authority and the pin state of a posting
     */
    getPinTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.posting.pinned) {
            return 'artemisApp.metis.removePinPostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && !this.posting.pinned) {
            return 'artemisApp.metis.pinPostTutorTooltip';
        }
        return 'artemisApp.metis.pinnedPostTooltip';
    }

    /**
     * provides the tooltip for the archive icon dependent on the user authority and the archive state of a posting
     */
    getArchiveTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.posting.archived) {
            return 'artemisApp.metis.removeArchivePostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && !this.posting.archived) {
            return 'artemisApp.metis.archivePostTutorTooltip';
        }
        return 'artemisApp.metis.archivedPostTooltip';
    }
}
