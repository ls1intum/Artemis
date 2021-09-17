import { Component, OnChanges, OnInit } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/postings-reactions-bar/postings-reactions-bar.component';
import { DisplayPriority } from 'app/shared/metis/metis.util';

@Component({
    selector: 'jhi-post-reactions-bar',
    templateUrl: './post-reactions-bar.component.html',
    styleUrls: ['../postings-reactions-bar.component.scss'],
})
export class PostReactionsBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges {
    pinTooltip: string;
    archiveTooltip: string;
    displayPriority: DisplayPriority;
    readonly DisplayPriority = DisplayPriority;

    /**
     * on initialization: call resetTooltipsAndPriority
     */
    ngOnInit() {
        super.ngOnInit();
        this.resetTooltipsAndPriority();
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
     * changes the the state of the displayPriority property on a post to PINNED by invoking the metis service
     * in case the displayPriority is already set to PINNED, it will changed to NONE
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
     * changes the the state of the displayPriority property on a post to ARCHIVED by invoking the metis service,
     * in case the displayPriority is already set to ARCHIVED, it will changed to NONE
     */
    toggleArchive() {
        if (this.displayPriority === DisplayPriority.ARCHIVED) {
            this.displayPriority = DisplayPriority.NONE;
        } else {
            this.displayPriority = DisplayPriority.ARCHIVED;
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
