import { Directive, Input, OnChanges, OnInit } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';

const PIN_EMOJI_ID = 'pushpin';
const PIN_EMOJI_UNICODE = '1F4CC';
const ARCHIVE_EMOJI_ID = 'open_file_folder';
const ARCHIVE_EMOJI_UNICODE = '1F4C2';

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
    categoriesIcons: { [key: string]: string } = {
        recent: `M10 1h3v21h-3zm10.186 4l1.5 2.598L3.5 18.098 2 15.5zM2 7.598L3.5 5l18.186 10.5-1.5 2.598z`,
    };

    @Input() posting: T;
    showReactionSelector = false;
    selectedCourseEmojis: string[];
    reactionCountMap: ReactionCountMap = {};
    emojisToShowFilter: (emoji: string | EmojiData) => boolean = (emoji) => {
        if (typeof emoji === 'string') {
            return emoji !== PIN_EMOJI_UNICODE && emoji !== ARCHIVE_EMOJI_UNICODE;
        } else {
            return emoji.unified !== PIN_EMOJI_UNICODE && emoji.unified !== ARCHIVE_EMOJI_UNICODE;
        }
    };
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
                count: a[b.emojiId!] ? a[b.emojiId!].count + 1 : 1,
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
