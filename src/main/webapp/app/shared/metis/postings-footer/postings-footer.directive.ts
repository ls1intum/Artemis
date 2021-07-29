import { Directive, Input } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';

interface ReactionEvent {
    $event: Event;
    emoji?: EmojiData;
}

@Directive()
export abstract class PostingsFooterDirective<T extends Posting> {
    @Input() posting: T;
    showReactionSelector = false;

    protected constructor(protected metisService: MetisService) {}

    toggleReactionSelector() {
        this.showReactionSelector = !this.showReactionSelector;
    }

    abstract buildReaction(emojiData: EmojiData): Reaction;

    /**
     * updates the reaction based on the emitted event
     */
    updateReaction(reactionEvent: ReactionEvent): void {
        {
            if (reactionEvent.emoji !== undefined) {
                const existingReactionIdx = this.posting.reactions
                    ? this.posting.reactions.findIndex((reaction) => reaction.user?.id === this.metisService.getUser().id && reaction.emojiId === reactionEvent.emoji?.id)
                    : -1;
                if (this.posting.reactions && existingReactionIdx > -1) {
                    this.metisService.deleteReaction(this.posting.reactions[existingReactionIdx]);
                } else {
                    this.metisService.createReaction(this.buildReaction(reactionEvent.emoji));
                }
            }
        }
    }
}
