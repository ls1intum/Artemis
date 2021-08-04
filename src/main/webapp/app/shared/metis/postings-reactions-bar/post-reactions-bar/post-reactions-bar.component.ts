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

    ngOnInit() {
        super.ngOnInit();
        this.pinTooltip = this.getPinTooltip();
        this.archiveTooltip = this.getArchiveTooltip();
    }

    ngOnChanges() {
        super.ngOnChanges();
        this.pinTooltip = this.getPinTooltip();
        this.archiveTooltip = this.getArchiveTooltip();
    }

    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        reaction.post = this.posting;
        return reaction;
    }

    togglePin() {
        this.metisService.updatePostPinState(this.posting, !this.posting.pinned).subscribe(() => {});
    }

    toggleArchive() {
        this.metisService.updatePostArchiveState(this.posting, !this.posting.archived).subscribe(() => {});
    }

    getPinTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.posting.pinned) {
            return 'artemisApp.metis.removePinPostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && !this.posting.pinned) {
            return 'artemisApp.metis.pinPostTutorTooltip';
        } else {
            return 'artemisApp.metis.pinnedPostTooltip';
        }
    }

    getArchiveTooltip(): string {
        if (this.currentUserIsAtLeastTutor && this.posting.archived) {
            return 'artemisApp.metis.removeArchivePostTutorTooltip';
        }
        if (this.currentUserIsAtLeastTutor && !this.posting.archived) {
            return 'artemisApp.metis.archivePostTutorTooltip';
        } else {
            return 'artemisApp.metis.archivedPostTooltip';
        }
    }
}
