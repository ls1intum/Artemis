import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Reaction } from 'app/entities/metis/reaction.model';
import { Post } from 'app/entities/metis/post.model';
import { PostingsReactionsBarDirective } from 'app/shared/metis/posting-reactions-bar/posting-reactions-bar.component';
import { DisplayPriority } from 'app/shared/metis/metis.util';
import { MetisService } from 'app/shared/metis/metis.service';
import { faSmile } from '@fortawesome/free-regular-svg-icons';
import { AnswerPost } from 'app/entities/metis/answer-post.model';

@Component({
    selector: 'jhi-post-reactions-bar',
    templateUrl: './post-reactions-bar.component.html',
    styleUrls: ['../posting-reactions-bar.component.scss'],
})
export class PostReactionsBarComponent extends PostingsReactionsBarDirective<Post> implements OnInit, OnChanges {
    pinTooltip: string;
    archiveTooltip: string;
    displayPriority: DisplayPriority;
    readonly DisplayPriority = DisplayPriority;

    // Icons
    farSmile = faSmile;

    @Input()
    readOnlyMode = false;
    @Input() showAnswers: boolean;
    @Input() sortedAnswerPosts: AnswerPost[];
    @Input() isCourseMessagesPage: boolean;

    @Output() showAnswersChange = new EventEmitter<boolean>();
    @Output() openPostingCreateEditModal = new EventEmitter<void>();
    @Output() openThread = new EventEmitter<void>();

    constructor(metisService: MetisService) {
        super(metisService);
    }

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
     * changes the state of the displayPriority property on a post to ARCHIVED by invoking the metis service,
     * in case the displayPriority is already set to ARCHIVED, it will be changed to NONE
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
