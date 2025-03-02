import { AfterViewInit, ChangeDetectorRef, Component, ElementRef, effect, inject, input, output, viewChild } from '@angular/core';
import { faShare } from '@fortawesome/free-solid-svg-icons';
import { Post } from 'app/entities/metis/post.model';
import { AnswerPost } from 'app/entities/metis/answer-post.model';
import { Posting } from 'app/entities/metis/posting.model';
import dayjs from 'dayjs/esm';
import { Conversation } from 'app/entities/metis/conversation/conversation.model';
import { ProfilePictureComponent } from 'app/shared/profile-picture/profile-picture.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PostingContentComponent } from 'app/shared/metis/posting-content/posting-content.components';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { NgClass } from '@angular/common';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

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
    originalPostDetails = input<Posting | null>();
    messageContent = viewChild<ElementRef>('messageContent');
    isContentLong = false;
    showFullForwardedMessage = false;
    postingIsOfToday = false;

    protected viewButtonVisible = false;
    protected hasOriginalPostBeenDeleted = false;

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
                    if (post === null) {
                        this.hasOriginalPostBeenDeleted = true;
                    }
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

    toggleShowFullForwardedMessage(): void {
        this.showFullForwardedMessage = !this.showFullForwardedMessage;
    }

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

    updateSourceName() {
        if (!this.conversation) {
            this.sourceName = '';
        } else if (this.conversation?.type?.valueOf() === 'channel') {
            if (this.isAnswerPost) {
                this.sourceName = (this.conversation as any)?.name ? `a thread in #${(this.conversation as any)?.name} |` : 'a thread in #unknown |';
            } else {
                this.sourceName = (this.conversation as any)?.name ? `#${(this.conversation as any)?.name} |` : '#unknown |';
            }
        } else if (this.conversation?.type?.valueOf() === 'oneToOneChat') {
            this.sourceName = this.isAnswerPost ? 'a thread in a direct message ' : 'a direct message ';
        } else {
            this.sourceName = this.isAnswerPost ? 'a thread in a group message ' : 'a group message ';
        }
    }
}
