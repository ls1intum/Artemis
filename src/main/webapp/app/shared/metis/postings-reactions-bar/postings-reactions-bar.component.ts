import { Directive, Input, OnChanges, OnInit } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';

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
    categoriesIcons: { [key: string]: string } = {
        recent: `M10 1h3v21h-3zm10.186 4l1.5 2.598L3.5 18.098 2 15.5zM2 7.598L3.5 5l18.186 10.5-1.5 2.598z`,
    };

    @Input() posting: T;
    showReactionSelector = false;
    currentUserIsAtLeastTutor: boolean;

    /*
    Currently predefined fixed set of emojis that should be used within a course,
    they will be listed on first page of the emoji-mart selector
     */
    selectedCourseEmojis: string[];

    /*
    A map that lists associated reaction (by emojiId) for the current posting together with its count
    and a flag that indicates if the current user has used this reaction
     */
    reactionCountMap: ReactionCountMap = {};

    constructor(protected metisService: MetisService) {
        this.selectedCourseEmojis = ['smile', 'joy', 'sunglasses', 'tada', 'rocket', 'heavy_plus_sign', 'thumbsup', 'memo', 'coffee', 'recycle'];
    }

    /**
     * on initialization: updates the current posting and its reactions,
     * invokes metis service to check user authority
     */
    ngOnInit(): void {
        this.updatePostingWithReactions();
        this.currentUserIsAtLeastTutor = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    /**
     * on changes: updates the current posting and its reactions,
     * invokes metis service to check user authority
     */
    ngOnChanges(): void {
        this.updatePostingWithReactions();
        this.currentUserIsAtLeastTutor = this.metisService.metisUserIsAtLeastTutorInCourse();
    }

    abstract buildReaction(emojiId: string): Reaction;

    /**
     * updates the reaction based on the ReactionEvent emitted by the emoji-mart selector component
     */
    selectReaction(reactionEvent: ReactionEvent): void {
        if (reactionEvent.emoji !== undefined) {
            this.addOrRemoveReaction(reactionEvent.emoji.id);
        }
    }

    /**
     * updates the reaction based when a displayed emoji reaction is clicked,
     * i.e. when agree on an existing reaction (+1) or removing own reaction (-1)
     */
    updateReaction(emojiId: string): void {
        if (emojiId !== undefined) {
            this.addOrRemoveReaction(emojiId);
        }
    }

    /**
     * adds or removes a reaction by invoking the metis service,
     * depending on if the current user already reacted with the given emojiId (remove) or not (add)
     * @param emojiId emojiId representing the reaction to be added/removed
     */
    addOrRemoveReaction(emojiId: string): void {
        const existingReactionIdx = this.posting.reactions
            ? this.posting.reactions.findIndex((reaction) => reaction.user?.id === this.metisService.getUser().id && reaction.emojiId === emojiId)
            : -1;
        if (this.posting.reactions && existingReactionIdx > -1) {
            const reactionToDelete = this.posting.reactions[existingReactionIdx];
            this.metisService.deleteReaction(reactionToDelete).subscribe(() => {
                this.showReactionSelector = false;
            });
        } else {
            const reactionToCreate = this.buildReaction(emojiId);
            this.metisService.createReaction(reactionToCreate).subscribe(() => {
                this.showReactionSelector = false;
            });
        }
    }

    /**
     * builds the ReactionCountMap data structure out of a given array of reactions
     * @param reactions array of reactions associated to the current posting
     */
    buildEmojiIdCountMap(reactions: Reaction[]): ReactionCountMap {
        return reactions.reduce((a: ReactionCountMap, b: Reaction) => {
            const hasReacted = b.user?.id === this.metisService.getUser().id;
            const reactionCount = {
                count: a[b.emojiId!] ? a[b.emojiId!].count + 1 : 1,
                hasReacted: a[b.emojiId!] ? a[b.emojiId!].hasReacted || hasReacted : hasReacted,
            };
            return { ...a, [b.emojiId!]: reactionCount };
        }, {});
    }

    /**
     * updates the posting's reactions by calling the build function for the reactionCountMap if there are any reaction on the posting
     */
    updatePostingWithReactions(): void {
        if (this.posting.reactions && this.posting.reactions.length > 0) {
            this.reactionCountMap = this.buildEmojiIdCountMap(this.posting.reactions!);
        } else {
            this.reactionCountMap = {};
        }
    }
}
