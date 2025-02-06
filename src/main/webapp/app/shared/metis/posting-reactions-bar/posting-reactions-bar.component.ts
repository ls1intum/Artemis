import { Component, OnChanges, OnInit, inject, input, output, viewChild } from '@angular/core';
import { Posting } from 'app/entities/metis/posting.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { EmojiData } from '@ctrl/ngx-emoji-mart/ngx-emoji';
import { Reaction } from 'app/entities/metis/reaction.model';
import { PLACEHOLDER_USER_REACTED, ReactingUsersOnPostingPipe } from 'app/shared/pipes/reacting-users-on-posting.pipe';
import { faArrowRight, faBookmark, faCheck, faPencilAlt, faSmile, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { EmojiComponent } from 'app/shared/metis/emoji/emoji.component';
import { EmojiPickerComponent } from 'app/shared/metis/emoji/emoji-picker.component';
import { ConfirmIconComponent } from 'app/shared/confirm-icon/confirm-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CdkConnectedOverlay, CdkOverlayOrigin } from '@angular/cdk/overlay';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AsyncPipe, KeyValuePipe, NgClass } from '@angular/common';
import { AccountService } from 'app/core/auth/account.service';
import { DisplayPriority } from '../metis.util';
import { Post } from 'app/entities/metis/post.model';
import { Conversation, ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { getAsChannelDTO, isChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import dayjs from 'dayjs';
import { PostCreateEditModalComponent } from 'app/shared/metis/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

const PIN_EMOJI_ID = 'pushpin';
const ARCHIVE_EMOJI_ID = 'open_file_folder';
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

@Component({
    selector: 'jhi-posting-reactions-bar',
    templateUrl: './posting-reactions-bar.component.html',
    styleUrls: ['./posting-reactions-bar.component.scss'],
    imports: [
        NgbTooltip,
        EmojiComponent,
        CdkOverlayOrigin,
        FaIconComponent,
        TranslateDirective,
        CdkConnectedOverlay,
        EmojiPickerComponent,
        ConfirmIconComponent,
        AsyncPipe,
        KeyValuePipe,
        ArtemisTranslatePipe,
        ReactingUsersOnPostingPipe,
        NgClass,
        PostCreateEditModalComponent,
    ],
})
export class PostingReactionsBarComponent<T extends Posting> implements OnInit, OnChanges {
    protected metisService = inject(MetisService);
    private accountService = inject(AccountService);

    pinEmojiId: string = PIN_EMOJI_ID;
    archiveEmojiId: string = ARCHIVE_EMOJI_ID;
    closeCrossId: string = HEAVY_MULTIPLICATION_ID;

    posting = input<T>();
    isThreadSidebar = input<boolean>();
    isEmojiCount = input<boolean>(false);
    isReadOnlyMode = input<boolean>(false);

    openPostingCreateEditModal = output<void>();
    closePostingCreateEditModal = output<void>();
    reactionsUpdated = output<Reaction[]>();
    isModalOpen = output<void>();

    showReactionSelector = false;
    isAtLeastTutorInCourse: boolean;
    isAuthorOfPosting: boolean;
    isAuthorOfOriginalPost: boolean;
    isAnswerOfAnnouncement: boolean;
    isAtLeastInstructorInCourse: boolean;
    mayDeleteOutput = output<boolean>();
    mayEditOutput = output<boolean>();
    canPinOutput = output<boolean>();
    showAnswers = input<boolean>();
    sortedAnswerPosts = input<AnswerPost[]>();
    isCommunicationPage = input<boolean>();
    lastReadDate = input<dayjs.Dayjs>();
    previewMode = input<boolean>();
    hoverBar = input<boolean>(true);

    showAnswersChange = output<boolean>();
    isLastAnswer = input<boolean>(false);
    postingUpdated = output<void>();
    mayEdit: boolean;
    mayDelete: boolean;
    pinTooltip: string;
    displayPriority: DisplayPriority;
    canPin = false;
    readonly DisplayPriority = DisplayPriority;

    isDeleteEvent = output<boolean>();
    readonly onBookmarkClicked = output<void>();
    openThread = output<void>();

    // Icons
    readonly faBookmark = faBookmark;
    readonly faSmile = faSmile;
    readonly faCheck = faCheck;
    readonly faPencilAlt = faPencilAlt;
    readonly faArrowRight = faArrowRight;
    readonly faTrash = faTrashAlt;

    createEditModal = viewChild.required<PostCreateEditModalComponent>('createEditModal');

    /**
     * on initialization: updates the current posting and its reactions,
     * invokes metis service to check user authority
     */
    ngOnInit() {
        this.updatePostingWithReactions();
        this.isAuthorOfPosting = this.metisService.metisUserIsAuthorOfPosting(this.posting() as Posting);
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        this.isAtLeastInstructorInCourse = this.metisService.metisUserIsAtLeastInstructorInCourse();
        this.isAnswerOfAnnouncement =
            this.getPostingType() === 'answerPost' ? (getAsChannelDTO((this.posting() as AnswerPost).post?.conversation)?.isAnnouncementChannel ?? false) : false;
        this.isAuthorOfOriginalPost = this.getPostingType() === 'answerPost' ? this.metisService.metisUserIsAuthorOfPosting((this.posting() as AnswerPost).post!) : false;

        if (this.getPostingType() === 'post') {
            const currentConversation = this.metisService.getCurrentConversation();
            this.setCanPin(currentConversation);
            this.resetTooltipsAndPriority();
        }
        this.setMayDelete();
        this.setMayEdit();
    }

    /**
     * on changes: updates the current posting and its reactions,
     * invokes metis service to check user authority
     */
    ngOnChanges() {
        this.updatePostingWithReactions();
        this.isAtLeastTutorInCourse = this.metisService.metisUserIsAtLeastTutorInCourse();
        if (this.getPostingType() === 'post') {
            this.resetTooltipsAndPriority();
        }
        this.setMayDelete();
        this.setMayEdit();
    }

    /*
     * icons (as svg paths) to be used as category preview image in emoji mart selector
     */
    categoriesIcons: { [key: string]: string } = {
        // category 'recent' (would show recently used emojis) is overwritten by a preselected set of emojis for that course,
        // therefore category icon is an asterisk (indicating customization) instead of a clock (indicating the "recently used"-use case)
        recent: `M10 1h3v21h-3zm10.186 4l1.5 2.598L3.5 18.098 2 15.5zM2 7.598L3.5 5l18.186 10.5-1.5 2.598z`,
    };

    /**
     * Checks whether the user can pin the message in the conversation
     *
     * @param currentConversation the conversation the post belongs to
     */
    setCanPin(currentConversation: ConversationDTO | undefined) {
        if (!currentConversation) {
            this.canPin = this.metisService.metisUserIsAtLeastTutorInCourse();
            return;
        }

        if (isChannelDTO(currentConversation)) {
            this.canPin = currentConversation.hasChannelModerationRights ?? false;
        } else if (isGroupChatDTO(currentConversation)) {
            this.canPin = currentConversation.creator?.id === this.accountService.userIdentity?.id;
        } else if (isOneToOneChatDTO(currentConversation)) {
            this.canPin = true;
        }
        this.canPinOutput.emit(this.canPin);
    }

    private resetTooltipsAndPriority() {
        this.displayPriority = (this.posting() as Post).displayPriority!;
        this.pinTooltip = this.getPinTooltip();
    }

    getShowNewMessageIcon(): boolean {
        let showIcon = false;
        // iterate over all answer posts
        (this.sortedAnswerPosts() as unknown as AnswerPost[]).forEach((answerPost: Posting) => {
            // check if the answer post is newer than the last read date
            const isAuthor = this.metisService.metisUserIsAuthorOfPosting(answerPost);
            const lastReadDate = this.lastReadDate?.();
            const creationDate = answerPost.creationDate;

            if (lastReadDate && creationDate) {
                const lastReadDateDayJs = dayjs(lastReadDate);
                // @ts-ignore
                if (!isAuthor && creationDate.isAfter(lastReadDateDayJs)) {
                    showIcon = true;
                }
            }
        });
        return showIcon;
    }

    /**
     * provides the tooltip for the pin icon dependent on the user authority and the pin state of a posting
     *
     */
    getPinTooltip(): string {
        if (this.canPin && this.displayPriority === DisplayPriority.PINNED) {
            return 'artemisApp.metis.removePinPostTooltip';
        }
        if (this.canPin && this.displayPriority !== DisplayPriority.PINNED) {
            return 'artemisApp.metis.pinPostTooltip';
        }
        return 'artemisApp.metis.pinnedPostTooltip';
    }

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

    /**
     * builds and returns a Reaction model out of an emojiId and thereby sets the answerPost property properly
     * @param emojiId emojiId to build the model for
     */
    buildReaction(emojiId: string): Reaction {
        const reaction = new Reaction();
        reaction.emojiId = emojiId;
        if (this.getPostingType() === 'answerPost') {
            reaction.answerPost = this.posting() as AnswerPost;
        } else {
            reaction.post = this.posting() as Post;
        }
        return reaction;
    }

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
        const existingReactionIdx = (this.posting() as Posting).reactions
            ? (this.posting() as Posting).reactions!.findIndex((reaction) => reaction.user?.id === this.metisService.getUser().id && reaction.emojiId === emojiId)
            : -1;
        if ((this.posting() as Posting).reactions && existingReactionIdx > -1) {
            const reactionToDelete = (this.posting() as Posting).reactions![existingReactionIdx];
            this.metisService.deleteReaction(reactionToDelete).subscribe(() => {
                (this.posting() as Posting).reactions = (this.posting() as Posting).reactions?.filter((reaction) => reaction.id !== reactionToDelete.id);
                this.updatePostingWithReactions();
                this.showReactionSelector = false;
                this.reactionsUpdated.emit((this.posting() as Posting).reactions!);
            });
        } else {
            const reactionToCreate = this.buildReaction(emojiId);
            this.metisService.createReaction(reactionToCreate).subscribe(() => {
                this.updatePostingWithReactions();
                this.showReactionSelector = false;
                this.reactionsUpdated.emit((this.posting() as Posting).reactions || []);
            });
        }
    }

    /**
     * updates the posting's reactions by calling the build function for the reactionMetaDataMap if there are any reaction on the posting
     */
    updatePostingWithReactions(): void {
        if ((this.posting() as Posting).reactions && (this.posting() as Posting).reactions!.length > 0) {
            // filter out emoji for pin and archive as they should not be listed in the reactionMetaDataMap
            const filteredReactions = (this.posting() as Posting).reactions!.filter(
                (reaction: Reaction) => reaction.emojiId !== this.pinEmojiId || reaction.emojiId !== this.archiveEmojiId,
            );
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
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            const reactingUser = hasReacted ? PLACEHOLDER_USER_REACTED : reaction.user?.name!;
            const reactionMetaData: ReactionMetaData = {
                count: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].count + 1 : 1,
                hasReacted: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].hasReacted || hasReacted : hasReacted,
                reactingUsers: metaDataMap[reaction.emojiId!] ? metaDataMap[reaction.emojiId!].reactingUsers.concat(reactingUser) : [reactingUser],
            };
            return { ...metaDataMap, [reaction.emojiId!]: reactionMetaData };
        }, {});
    }

    protected bookmarkPosting() {
        this.onBookmarkClicked.emit();
    }

    isAnyReactionCountAboveZero(): boolean {
        return Object.values(this.reactionMetaDataMap).some((reaction) => reaction.count >= 1);
    }

    /**
     * invokes the metis service to delete posting
     */
    deletePosting(): void {
        this.isDeleteEvent.emit(true);
    }

    /**
     * changes the state of the displayPriority property on a post to PINNED by invoking the metis service
     * in case the displayPriority is already set to PINNED, it will be changed to NONE
     */
    togglePin() {
        if (this.canPin) {
            if (this.displayPriority === DisplayPriority.PINNED) {
                this.displayPriority = DisplayPriority.NONE;
            } else {
                this.displayPriority = DisplayPriority.PINNED;
            }
            (this.posting() as Post).displayPriority = this.displayPriority;
            this.metisService.updatePostDisplayPriority((this.posting() as Posting).id!, this.displayPriority).subscribe();
        }
    }

    /**
     * toggles the resolvesPost property of an answer post if the user is at least tutor in a course or the user is the author of the original post,
     * delegates the update to the metis service
     */
    toggleResolvesPost(): void {
        if (this.isAtLeastTutorInCourse || this.isAuthorOfOriginalPost) {
            (this.posting() as AnswerPost).resolvesPost = !(this.posting() as AnswerPost).resolvesPost;
            this.metisService.updateAnswerPost(this.posting() as AnswerPost).subscribe();
        }
    }

    checkIfPinned(): DisplayPriority {
        return this.displayPriority;
    }

    openAnswerView() {
        this.showAnswersChange.emit(true);
        this.openPostingCreateEditModal.emit();
    }

    closeAnswerView() {
        this.showAnswersChange.emit(false);
        this.closePostingCreateEditModal.emit();
    }

    setMayEdit(): void {
        this.mayEdit = this.isAuthorOfPosting;
        this.mayEditOutput.emit(this.mayEdit);
    }

    editPosting() {
        if (this.getPostingType() === 'post') {
            if ((this.posting() as Post)!.title != '') {
                this.createEditModal().open();
            } else {
                this.isModalOpen.emit();
            }
        } else {
            this.openPostingCreateEditModal.emit();
        }
    }

    setMayDelete(): void {
        const conversation = this.getConversation();
        const channel = getAsChannelDTO(conversation);

        const isAnswerOfAnnouncement = this.getPostingType() === 'answerPost' ? (channel?.isAnnouncementChannel ?? false) : false;
        const isCourseWide = channel?.isCourseWide ?? false;

        const canDeleteAnnouncement = isAnswerOfAnnouncement ? this.isAtLeastInstructorInCourse : true;
        const mayDeleteOtherUsers =
            (isCourseWide && this.isAtLeastTutorInCourse) || (getAsChannelDTO(this.metisService.getCurrentConversation())?.hasChannelModerationRights ?? false);

        this.mayDelete = !this.isReadOnlyMode() && !this.previewMode() && (this.isAuthorOfPosting || mayDeleteOtherUsers) && canDeleteAnnouncement;
        this.mayDeleteOutput.emit(this.mayDelete);
    }

    private getConversation(): Conversation | undefined {
        if (this.getPostingType() === 'answerPost') {
            return (this.posting() as AnswerPost).post?.conversation;
        } else {
            return (this.posting() as Post).conversation;
        }
    }

    getPostingType(): 'post' | 'answerPost' {
        return this.posting() && 'post' in this.posting()! ? 'answerPost' : 'post';
    }

    getSaved(): boolean {
        return <boolean>(this.posting() as Posting)?.isSaved;
    }

    getResolvesPost(): boolean {
        return <boolean>(this.posting() as AnswerPost)?.resolvesPost;
    }
}
