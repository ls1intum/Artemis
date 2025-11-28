import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, effect, inject, input, output, viewChild } from '@angular/core';
import { faShare } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { Posting } from 'app/communication/shared/entities/posting.model';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/communication/shared/entities/conversation/conversation.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostingContentComponent } from 'app/communication/posting-content/posting-content.components';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { addPublicFilePrefix } from 'app/app.constants';

@Component({
    selector: 'jhi-forwarded-message',
    templateUrl: './forwarded-message.component.html',
    styleUrls: ['./forwarded-message.component.scss'],
    imports: [ProfilePictureComponent, TranslateDirective, NgClass, FaIconComponent, NgbTooltip, PostingContentComponent, ArtemisTranslatePipe, ArtemisDatePipe],
})
export class ForwardedMessageComponent implements AfterViewInit {
    readonly faShare = faShare;
    readonly onNavigateToPost = output<Posting>();

    sourceName: string | undefined = '';
    todayFlag?: string;

    /** the forwarded post (can be a Post or AnswerPost) */
    originalPostDetails = input<Posting | undefined>();
    messageContent = viewChild<ElementRef>('messageContent');
    isContentLong = false;
    showFullForwardedMessage = false;
    postingIsOfToday = false;

    /** Controls whether the "View" button should be shown */
    protected viewButtonVisible = false;
    hasOriginalPostBeenDeleted = input<boolean | undefined>();

    private cdr = inject(ChangeDetectorRef);
    private conversation: Conversation | undefined;
    private isAnswerPost = false;

    constructor() {
        effect(() => {
            try {
                const post = this.originalPostDetails();
                if (post) {
                    this.isAnswerPost = 'post' in post;
                    this.conversation = this.isAnswerPost ? (post as AnswerPost).post?.conversation : (post as Post).conversation;
                    this.updateSourceName();
                    this.isChannel();
                    this.postingIsOfToday = dayjs().isSame(post.creationDate, 'day');
                    this.todayFlag = this.getTodayFlag();
                } else {
                    this.sourceName = '';
                    this.conversation = undefined;
                    this.viewButtonVisible = false;
                    this.postingIsOfToday = false;
                    this.todayFlag = undefined;
                }
            } catch (error) {
                this.sourceName = '';
                this.conversation = undefined;
                this.viewButtonVisible = false;
            }
        });
    }

    ngAfterViewInit(): void {
        setTimeout(() => {
            this.checkIfContentOverflows();
        }, 0);
    }

    /** Toggles whether full message content should be shown */
    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage = !this.showFullForwardedMessage;
    }

    /**
     * Checks if the message content exceeds its container height
     * and sets a flag for showing the "expand" button.
     */
    checkIfContentOverflows(): void {
        if (this.messageContent()) {
            const nativeElement = this.messageContent()?.nativeElement;
            this.isContentLong = nativeElement.scrollHeight > nativeElement.clientHeight;
            this.cdr.detectChanges();
        }
    }

    /**
     * sets a flag that replaces the date by "Today" in the posting's header if applicable
     */
    getTodayFlag(): string | undefined {
        if (this.postingIsOfToday) {
            return 'artemisApp.metis.today';
        } else {
            return undefined;
        }
    }

    isChannel() {
        if (this.conversation?.type?.valueOf() === 'channel') {
            this.viewButtonVisible = true;
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
            this.sourceName = '';
        } else if (this.conversation?.type?.valueOf() === 'channel') {
            if (this.isAnswerPost) {
                const channelName = typeof this.conversation === 'object' ? (this.conversation as { name?: string }).name : undefined;
                this.sourceName = channelName ? `a thread in #${channelName} |` : 'a thread in #unknown |';
            } else {
                const channelName = typeof this.conversation === 'object' ? (this.conversation as { name?: string }).name : undefined;
                this.sourceName = channelName ? `#${channelName} |` : '#unknown |';
            }
        } else if (this.conversation?.type?.valueOf() === 'oneToOneChat') {
            this.sourceName = this.isAnswerPost ? 'a thread in a direct message ' : 'a direct message ';
        } else {
            this.sourceName = this.isAnswerPost ? 'a thread in a group message ' : 'a group message ';
        }
    }

    protected readonly addPublicFilePrefix = addPublicFilePrefix;
}
