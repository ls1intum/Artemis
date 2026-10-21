import { Component, ElementRef, afterRenderEffect, effect, input, output, signal, untracked, viewChild } from '@angular/core';
import { faShare } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/communication/shared/entities/conversation/conversation.model';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { ProfilePictureComponent } from 'app/shared-ui/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-forwarded-message',
    templateUrl: './forwarded-message.component.html',
    styleUrls: ['./forwarded-message.component.scss'],
    imports: [ProfilePictureComponent, TranslateDirective, FaIconComponent, NgbTooltip, PostingContentComponent, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class ForwardedMessageComponent {
    readonly faShare = faShare;
    readonly onNavigateToPost = output<Posting>();

    readonly sourceName = signal<string | undefined>('');
    readonly todayFlag = signal<string | undefined>(undefined);

    /** the forwarded post (can be a Post or AnswerPost) */
    originalPostDetails = input<Posting | undefined>();
    readonly messageContent = viewChild<ElementRef<HTMLElement>>('messageContent');
    private readonly messageContentInner = viewChild<ElementRef<HTMLElement>>('messageContentInner');
    readonly isContentLong = signal(false);
    readonly showFullForwardedMessage = signal(false);
    readonly postingIsOfToday = signal(false);

    /** Controls whether the "View" button should be shown */
    protected readonly viewButtonVisible = signal(false);
    hasOriginalPostBeenDeleted = input<boolean | undefined>();

    private conversation: Conversation | undefined;
    private isAnswerPost = false;
    private sourceId: number | undefined;

    /** Keeps source metadata reactive while preserving the reader's expansion across refetches of the same post or reply. */
    constructor() {
        effect(() => {
            const post = this.originalPostDetails();
            untracked(() => {
                const isAnswerPost = !!post && 'post' in post;
                if (post?.id === undefined || post.id !== this.sourceId || isAnswerPost !== this.isAnswerPost) {
                    this.isContentLong.set(false);
                    this.showFullForwardedMessage.set(false);
                }
                this.sourceId = post?.id;
                this.isAnswerPost = isAnswerPost;
                try {
                    if (post) {
                        this.conversation = this.isAnswerPost ? (post as AnswerPost).post?.conversation : (post as Post).conversation;
                        this.updateSourceName();
                        this.isChannel();
                        this.postingIsOfToday.set(dayjs().isSame(post.creationDate, 'day'));
                        this.todayFlag.set(this.getTodayFlag());
                    } else {
                        this.sourceName.set('');
                        this.conversation = undefined;
                        this.viewButtonVisible.set(false);
                        this.postingIsOfToday.set(false);
                        this.todayFlag.set(undefined);
                    }
                } catch (error) {
                    this.sourceName.set('');
                    this.conversation = undefined;
                    this.viewButtonVisible.set(false);
                }
            });
        });
        afterRenderEffect((onCleanup) => {
            // A refetched source may keep the same dimensions, so resize notifications alone are insufficient.
            this.originalPostDetails();
            const body = this.messageContent()?.nativeElement;
            const content = this.messageContentInner()?.nativeElement;
            if (!body || !content) {
                return;
            }
            // Markdown, images and forwarded sources can arrive after the first render. Observe the natural
            // content height as well as the clipped body so expansion stays available after those changes.
            // eslint-disable-next-line localRules/enforce-cleanup-on-destroy -- afterRenderEffect runs onCleanup below before re-running and on destruction.
            const observer = new ResizeObserver(() => this.checkIfContentOverflows());
            observer.observe(content);
            observer.observe(body);
            untracked(() => this.checkIfContentOverflows());
            onCleanup(() => observer.disconnect());
        });
    }

    /** Toggles whether full message content should be shown */
    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage.update((value) => !value);
    }

    /**
     * Checks if the message content exceeds its container height
     * and sets a flag for showing the "expand" button.
     */
    checkIfContentOverflows(): void {
        const body = this.messageContent()?.nativeElement;
        // Expanding removes the height limit. Keep the control visible so the user can collapse the message.
        if (body && !this.showFullForwardedMessage()) {
            this.isContentLong.set(body.scrollHeight > body.clientHeight);
        }
    }

    /**
     * sets a flag that replaces the date by "Today" in the posting's header if applicable
     */
    getTodayFlag(): string | undefined {
        if (this.postingIsOfToday()) {
            return 'artemisApp.communication.today';
        } else {
            return undefined;
        }
    }

    isChannel() {
        if (this.conversation?.type?.valueOf() === 'channel') {
            this.viewButtonVisible.set(true);
        }
    }

    onTriggerNavigateToPost() {
        if (!this.originalPostDetails()) {
            return;
        }
        this.onNavigateToPost.emit(this.originalPostDetails()!);
    }

    /**
     * Updates the name of the source of the forwarded message
     * based on the conversation type and post type (e.g., thread, channel, DM).
     */
    updateSourceName() {
        if (!this.conversation) {
            this.sourceName.set('');
        } else if (this.conversation?.type?.valueOf() === 'channel') {
            const channelName = (this.conversation as Channel).name;
            if (this.isAnswerPost) {
                this.sourceName.set(channelName ? `a thread in #${channelName} |` : 'a thread in #unknown |');
            } else {
                this.sourceName.set(channelName ? `#${channelName} |` : '#unknown |');
            }
        } else if (this.conversation?.type?.valueOf() === 'oneToOneChat') {
            this.sourceName.set(this.isAnswerPost ? 'a thread in a direct message ' : 'a direct message ');
        } else {
            this.sourceName.set(this.isAnswerPost ? 'a thread in a group message ' : 'a group message ');
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
