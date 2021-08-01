import { Directive, Input, OnChanges, OnInit } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';

const PIN_EMOJI_ID = 'pushpin';
const ARCHIVE_EMOJI_ID = 'open_file_folder';

interface ReactionEvent {
    $event: Event;
    emoji?: EmojiData;
}

interface ReactionCount {
    count: number;
    hasReacted: boolean;
}

interface ReactionCountMap {
    [emojiId: string]: ReactionCount;
}

@Directive()
export abstract class PostingsReactionsBarDirective<T extends Posting> implements OnInit, OnChanges {
    pinEmojiId: string = PIN_EMOJI_ID;
    archiveEmojiId: string = ARCHIVE_EMOJI_ID;

    @Input() posting: T;
    showReactionSelector = false;
    selectedCourseEmojis: string[];
    reactionCountMap: ReactionCountMap = {};
    emojisToShowFilter: (emojiId: string) => boolean = (emojiId) => emojiId !== this.pinEmojiId && emojiId !== this.archiveEmojiId;
    currentUserIsAtLeastTutor: boolean;

    constructor(protected metisService: MetisService) {
        this.selectedCourseEmojis = ['smile', 'joy', 'sunglasses', 'tada', 'rocket', 'heavy_plus_sign', 'thumbsup', 'memo', 'coffee', 'recycle'];
    }

    ngOnInit(): void {
        this.updatePostingWithReactions();
        this.currentUserIsAtLeastTutor = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    ngOnChanges(): void {
        this.updatePostingWithReactions();
        this.currentUserIsAtLeastTutor = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    abstract buildReaction(emojiId: string): Reaction;

    /**
     * updates the reaction based on the emitted event
     */
    selectReaction(reactionEvent: ReactionEvent): void {
        if (reactionEvent.emoji !== undefined) {
            this.addOrRemoveReaction(reactionEvent.emoji.id);
        }
    }

    updateReaction(emojiId: string): void {
        if (emojiId !== undefined) {
            this.addOrRemoveReaction(emojiId);
        }
    }

    addOrRemoveReaction(emojiId: string): void {
        const existingReactionIdx = this.posting.reactions
            ? this.posting.reactions.findIndex((reaction) => reaction.user?.id === this.metisService.getUser().id && reaction.emojiId === emojiId)
            : -1;
        if (this.posting.reactions && existingReactionIdx > -1) {
            this.metisService.deleteReaction(this.posting.reactions[existingReactionIdx]).subscribe(() => {
                this.showReactionSelector = false;
            });
        } else {
            this.metisService.createReaction(this.buildReaction(emojiId)).subscribe(() => {
                this.showReactionSelector = false;
            });
        }
    }

    buildEmojiIdCountMap(reactions: Reaction[]): ReactionCountMap {
        return reactions.reduce((a: ReactionCountMap, b: Reaction) => {
            if (b.emojiId === this.pinEmojiId || b.emojiId === this.archiveEmojiId) {
                return a;
            }
            const hasReacted = b.user?.id === this.metisService.getUser().id;
            const reactionCount = {
                count: a[b.emojiId!] ? a[b.emojiId!].count++ : 1,
                hasReacted: a[b.emojiId!] ? a[b.emojiId!].hasReacted || hasReacted : hasReacted,
            };
            return { ...a, [b.emojiId!]: reactionCount };
        }, {});
    }

    updatePostingWithReactions(): void {
        if (this.posting.reactions && this.posting.reactions.length > 0) {
            this.reactionCountMap = this.buildEmojiIdCountMap(this.posting.reactions!);
        } else {
            this.reactionCountMap = {};
        }
    }
}
