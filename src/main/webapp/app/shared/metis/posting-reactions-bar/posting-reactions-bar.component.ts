import { Directive, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PLACEHOLDER_USER_REACTED } from 'app/shared/pipes/reacting-users-on-posting.pipe';

const PIN_EMOJI_ID = 'pushpin';
const ARCHIVE_EMOJI_ID = 'open_file_folder';
const SPEECH_BALLOON_ID = 'speech_balloon';
const HEAVY_MULTIPLICATION_ID = 'heavy_multiplication_x';

const SPEECH_BALLOON_UNICODE = '1F4AC';
const ARCHIVE_EMOJI_UNICODE = '1F4C2';
const PIN_EMOJI_UNICODE = '1F4CC';
const HEAVY_MULTIPLICATION_UNICODE = '2716';

/**
 * event triggered by the emoji mart component, including EmojiData
 */
interface ReactionEvent {
    $event: Event;
    emoji?: EmojiData;
}

/**
 * represents the amount of users that reacted
 * hasReacted indicates if the currently logged-in user is among those counted users
 */
interface ReactionMetaData {
    count: number;
    hasReacted: boolean;
    reactingUsers: string[];
}

/**
 * data structure used for displaying emoji reactions with metadata on postings
 */
interface ReactionMetaDataMap {
    [emojiId: string]: ReactionMetaData;
}

@Directive()
export abstract class PostingsReactionsBarDirective<T extends Posting> implements OnInit, OnChanges {
    pinEmojiId: string = PIN_EMOJI_ID;
    archiveEmojiId: string = ARCHIVE_EMOJI_ID;
    speechBalloonId: string = SPEECH_BALLOON_ID;
    closeCrossId: string = HEAVY_MULTIPLICATION_ID;

    @Input() posting: T;
    @Input() isThreadSidebar: boolean;
    @Output() openPostingCreateEditModal = new EventEmitter<void>();

    showReactionSelector = false;
    currentUserIsAtLeastTutor: boolean;

    /*
     * icons (as svg paths) to be used as category preview image in emoji mart selector
     */
    categoriesIcons: { [key: string]: string } = {
        // category 'recent' (would show recently used emojis) is overwritten by a preselected set of emojis for that course,
        // therefore category icon is an asterisk (indicating customization) instead of a clock (indicating the "recently used"-use case)
        recent: `M10 1h3v21h-3zm10.186 4l1.5 2.598L3.5 18.098 2 15.5zM2 7.598L3.5 5l18.186 10.5-1.5 2.598z`,
    };

    /**
     * currently predefined fixed set of emojis that should be used within a course,
     * they will be listed on first page of the emoji-mart selector
     */
    selectedCourseEmojis = ['smile', 'joy', 'sunglasses', 'tada', 'rocket', 'heavy_plus_sign', 'thumbsup', 'memo', 'coffee', 'recycle'];

    /**
     * emojis that have a predefined meaning, i.e. pin and archive emoji,
     * should not appear in the emoji-mart selector
     */
    emojisToShowFilter: (emoji: string | EmojiData) => boolean = (emoji) => {
        if (typeof emoji === 'string') {
            return emoji !== PIN_EMOJI_UNICODE && emoji !== ARCHIVE_EMOJI_UNICODE && emoji !== SPEECH_BALLOON_UNICODE && emoji !== HEAVY_MULTIPLICATION_UNICODE;
        } else {
            return (
                emoji.unified !== PIN_EMOJI_UNICODE &&
                emoji.unified !== ARCHIVE_EMOJI_UNICODE &&
                emoji.unified !== SPEECH_BALLOON_UNICODE &&
                emoji.unified !== HEAVY_MULTIPLICATION_UNICODE
            );
        }
    };

    /**
     * map that lists associated reaction (by emojiId) for the current posting together with its count
     * and a flag that indicates if the current user has used this reaction
     */
    reactionMetaDataMap: ReactionMetaDataMap = {};

    protected constructor(protected metisService: MetisService) {}

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
        if (reactionEvent.emoji != undefined) {
            this.addOrRemoveReaction(reactionEvent.emoji.id);
        }
    }

    /**
     * opens the emoji selector overlay if user clicks the '.reaction-button'
     * closes the emoji selector overly if user clicks the '.reaction-button' again or somewhere outside the overlay
     */
    toggleEmojiSelect() {
        this.showReactionSelector = !this.showReactionSelector;
    }

    /**
     * updates the reaction based when a displayed emoji reaction is clicked,
     * i.e. when agree on an existing reaction (+1) or removing own reactions (-1)
     */
    updateReaction(emojiId: string): void {
        if (emojiId != undefined) {
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
     * updates the posting's reactions by calling the build function for the reactionMetaDataMap if there are any reaction on the posting
     */
    updatePostingWithReactions(): void {
        if (this.posting.reactions && this.posting.reactions.length > 0) {
            // filter out emoji for pin and archive as they should not be listed in the reactionMetaDataMap
            const filteredReactions = this.posting.reactions.filter((reaction: Reaction) => reaction.emojiId !== this.pinEmojiId || reaction.emojiId !== this.archiveEmojiId);
            this.reactionMetaDataMap = this.buildReactionMetaDataMap(filteredReactions);
        } else {
            this.reactionMetaDataMap = {};
        }
    }

    /**
     * builds the ReactionMetaDataMap data structure out of a given array of reactions
     * @param reactions array of reactions associated to the current posting
     */
    buildReactionMetaDataMap(reactions: Reaction[]): ReactionMetaDataMap {
        return reactions.reduce((metaDataMap: ReactionMetaDataMap, reaction: Reaction) => {
            const hasReacted = reaction.user?.id === this.metisService.getUser().id;
            const reactingUser = hasReacted ? PLACEHOLDER_USER_REACTED : reaction.user?.name!;
            const reactionMetaData: ReactionMetaData = {
                count: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].count + 1 : 1,
                hasReacted: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].hasReacted || hasReacted : hasReacted,
                reactingUsers: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].reactingUsers.concat(reactingUser) : [reactingUser],
            };
            return { ...metaDataMap, [reaction.emojiId!]: reactionMetaData };
        }, {});
    }
}
